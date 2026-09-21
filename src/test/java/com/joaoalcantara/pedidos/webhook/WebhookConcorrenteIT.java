package com.joaoalcantara.pedidos.webhook;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.joaoalcantara.pedidos.comum.TesteDeIntegracao;
import com.joaoalcantara.pedidos.pagamento.dominio.Pagamento;
import com.joaoalcantara.pedidos.pagamento.dominio.PagamentoRepositorio;
import com.joaoalcantara.pedidos.pagamento.dominio.StatusPagamento;
import com.joaoalcantara.pedidos.pedido.aplicacao.PedidoServico;
import com.joaoalcantara.pedidos.pedido.api.ItemRequisicao;
import com.joaoalcantara.pedidos.pedido.api.PedidoRequisicao;
import com.joaoalcantara.pedidos.pedido.dominio.Pedido;
import com.joaoalcantara.pedidos.produto.dominio.Produto;
import com.joaoalcantara.pedidos.produto.dominio.ProdutoRepositorio;
import com.joaoalcantara.pedidos.seguranca.UsuarioAutenticado;
import com.joaoalcantara.pedidos.usuario.dominio.Papel;
import com.joaoalcantara.pedidos.usuario.dominio.Usuario;
import com.joaoalcantara.pedidos.usuario.dominio.UsuarioRepositorio;
import com.joaoalcantara.pedidos.webhook.aplicacao.WebhookServico;

/**
 * O teste central da etapa: o mesmo evento chegando ao mesmo tempo.
 *
 * <p>Uma consulta do tipo "ja processei este evento?" antes de aplicar o efeito
 * passa nos testes sequenciais e falha aqui: as N threads consultam, todas veem
 * "ainda nao", e todas baixam o estoque. A restricao de unicidade gravada na
 * mesma transacao do efeito e o que fecha essa janela.</p>
 */
@TesteDeIntegracao
class WebhookConcorrenteIT {

    private static final int THREADS = 12;
    private static final String SEGREDO = "whsec_segredo_falso_de_teste";

    @Autowired
    private WebhookServico webhookServico;

    @Autowired
    private PedidoServico pedidoServico;

    @Autowired
    private ProdutoRepositorio produtos;

    @Autowired
    private UsuarioRepositorio usuarios;

    @Autowired
    private PagamentoRepositorio pagamentos;

    @Autowired
    private JdbcTemplate jdbc;

    private static String assinar(String corpo) {
        long carimbo = Instant.now().getEpochSecond();
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(SEGREDO.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String hmac = HexFormat.of()
                    .formatHex(mac.doFinal((carimbo + "." + corpo).getBytes(StandardCharsets.UTF_8)));
            return "t=%d,v1=%s".formatted(carimbo, hmac);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    @DisplayName("o mesmo evento entregue por N threads simultaneas baixa o estoque uma unica vez")
    void entregaSimultaneaDoMesmoEvento() throws InterruptedException {
        Usuario cliente = usuarios.salvar(new Usuario("Cliente", "cliente-" + UUID.randomUUID() + "@exemplo.com",
                "$2a$10$hashfalsoparateste000000000000000000000000000000000000", Papel.CLIENTE, Instant.now()));
        Produto produto = produtos.salvar(
                new Produto("Produto " + UUID.randomUUID(), "d", new BigDecimal("100.00"), 100));

        var resultado = pedidoServico.criar(
                new UsuarioAutenticado(cliente.getId(), cliente.getNome(), cliente.getPapel()),
                UUID.randomUUID().toString(),
                new PedidoRequisicao(List.of(new ItemRequisicao(produto.getId(), 5))));
        Pedido pedido = resultado.pedido();

        String idExterno = "pi_" + UUID.randomUUID();
        pagamentos.salvar(new Pagamento(pedido, idExterno, pedido.getValorTotal(),
                StatusPagamento.PENDENTE, Instant.now()));

        String idDoEvento = "evt_concorrente_" + UUID.randomUUID();
        String corpo = """
                {"id":"%s","object":"event","type":"payment_intent.succeeded","data":{"object":{"id":"%s","status":"succeeded"}}}"""
                .formatted(idDoEvento, idExterno);
        String assinatura = assinar(corpo);

        AtomicInteger processados = new AtomicInteger();
        AtomicInteger repetidos = new AtomicInteger();
        List<Throwable> inesperados = java.util.Collections.synchronizedList(new java.util.ArrayList<>());

        CountDownLatch largada = new CountDownLatch(1);
        CountDownLatch chegada = new CountDownLatch(THREADS);

        try (ExecutorService executor = Executors.newFixedThreadPool(THREADS)) {
            for (int i = 0; i < THREADS; i++) {
                executor.submit(() -> {
                    try {
                        largada.await();
                        var saida = webhookServico.receber(corpo, assinatura);
                        if (saida == WebhookServico.Resultado.PROCESSADO) {
                            processados.incrementAndGet();
                        } else {
                            repetidos.incrementAndGet();
                        }
                    } catch (Throwable e) {
                        inesperados.add(e);
                    } finally {
                        chegada.countDown();
                    }
                });
            }
            largada.countDown();
            assertThat(chegada.await(60, TimeUnit.SECONDS)).isTrue();
        }

        assertThat(inesperados)
                .as("entrega repetida nao e erro: nenhuma thread deve estourar")
                .isEmpty();
        assertThat(processados.get()).as("exatamente uma entrega aplica o efeito").isEqualTo(1);
        assertThat(repetidos.get()).isEqualTo(THREADS - 1);

        // O juiz final e o banco, nao os contadores em memoria.
        Integer registros = jdbc.queryForObject(
                "SELECT count(*) FROM eventos_processados WHERE id_externo = ?", Integer.class, idDoEvento);
        assertThat(registros).as("o evento foi registrado uma unica vez").isEqualTo(1);

        Integer eventosNoOutbox = jdbc.queryForObject(
                "SELECT count(*) FROM outbox WHERE agregado_id = ?", Integer.class,
                String.valueOf(pedido.getId()));
        assertThat(eventosNoOutbox)
                .as("doze entregas simultaneas, um unico pedido.pago publicado")
                .isEqualTo(1);

        // A baixa do estoque e do consumidor da fila (Etapa 9): no momento do
        // webhook, as unidades continuam reservadas.
        assertThat(produtos.porId(produto.getId()).orElseThrow().getEstoqueReservado()).isEqualTo(5);
    }
}
