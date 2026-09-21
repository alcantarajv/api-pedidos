package com.joaoalcantara.pedidos.consumo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.function.BooleanSupplier;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.joaoalcantara.pedidos.comum.TesteDeIntegracao;
import com.joaoalcantara.pedidos.consumo.dominio.NotificacaoRepositorio;
import com.joaoalcantara.pedidos.outbox.aplicacao.WorkerDoOutbox;
import com.joaoalcantara.pedidos.outbox.dominio.OutboxRepositorio;
import com.joaoalcantara.pedidos.pagamento.dominio.Pagamento;
import com.joaoalcantara.pedidos.pagamento.dominio.PagamentoRepositorio;
import com.joaoalcantara.pedidos.pagamento.dominio.StatusPagamento;
import com.joaoalcantara.pedidos.pedido.aplicacao.PedidoServico;
import com.joaoalcantara.pedidos.pedido.api.ItemRequisicao;
import com.joaoalcantara.pedidos.pedido.api.PedidoRequisicao;
import com.joaoalcantara.pedidos.pedido.dominio.Pedido;
import com.joaoalcantara.pedidos.pedido.dominio.PedidoRepositorio;
import com.joaoalcantara.pedidos.pedido.dominio.StatusPedido;
import com.joaoalcantara.pedidos.produto.dominio.Produto;
import com.joaoalcantara.pedidos.produto.dominio.ProdutoRepositorio;
import com.joaoalcantara.pedidos.seguranca.UsuarioAutenticado;
import com.joaoalcantara.pedidos.usuario.dominio.Papel;
import com.joaoalcantara.pedidos.usuario.dominio.Usuario;
import com.joaoalcantara.pedidos.usuario.dominio.UsuarioRepositorio;

/**
 * O caminho inteiro, ponta a ponta:
 *
 * <pre>
 * webhook assinado -> pedido PAGO + evento no outbox (mesma transacao)
 *                  -> worker publica e o broker confirma
 *                  -> consumidor de estoque baixa as unidades
 *                  -> consumidor de notificacao avisa o cliente
 * </pre>
 *
 * <p>Cada peca ja tem seu teste. Este existe para provar que elas se encaixam —
 * e um sistema de mensageria tem o costume de funcionar em partes e falhar na
 * emenda: binding errado, cabecalho que nao chega, payload que o consumidor nao
 * desserializa.</p>
 */
@TesteDeIntegracao
@AutoConfigureMockMvc
class FluxoCompletoIT {

    private static final String SEGREDO = "whsec_segredo_falso_de_teste";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PedidoServico pedidoServico;

    @Autowired
    private PedidoRepositorio pedidos;

    @Autowired
    private ProdutoRepositorio produtos;

    @Autowired
    private UsuarioRepositorio usuarios;

    @Autowired
    private PagamentoRepositorio pagamentos;

    @Autowired
    private OutboxRepositorio outbox;

    @Autowired
    private WorkerDoOutbox worker;

    @Autowired
    private NotificacaoRepositorio notificacoes;

    private static void esperarAte(String oQue, BooleanSupplier condicao) {
        Instant limite = Instant.now().plus(Duration.ofSeconds(10));
        while (Instant.now().isBefore(limite)) {
            if (condicao.getAsBoolean()) {
                return;
            }
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
        }
        throw new AssertionError("Tempo esgotado esperando: " + oQue);
    }

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
    @DisplayName("do webhook ate a notificacao, passando pelo outbox e pela fila")
    void doWebhookAteONotificar() throws Exception {
        Usuario cliente = usuarios.salvar(new Usuario("Cliente", "cliente-" + UUID.randomUUID() + "@exemplo.com",
                "$2a$10$hashfalsoparateste000000000000000000000000000000000000", Papel.CLIENTE, Instant.now()));
        Produto produto = produtos.salvar(
                new Produto("Produto " + UUID.randomUUID(), "d", new BigDecimal("150.00"), 20));

        var resultado = pedidoServico.criar(
                new UsuarioAutenticado(cliente.getId(), cliente.getNome(), cliente.getPapel()),
                UUID.randomUUID().toString(),
                new PedidoRequisicao(List.of(new ItemRequisicao(produto.getId(), 2))));
        Pedido pedido = resultado.pedido();

        String idExterno = "pi_" + UUID.randomUUID();
        pagamentos.salvar(new Pagamento(pedido, idExterno, pedido.getValorTotal(),
                StatusPagamento.PENDENTE, Instant.now()));

        long pendentesAntes = outbox.contarPendentes();

        // 1. O gateway avisa que o pagamento foi aprovado.
        String corpo = """
                {"id":"evt_%s","object":"event","type":"payment_intent.succeeded","data":{"object":{"id":"%s","status":"succeeded"}}}"""
                .formatted(UUID.randomUUID(), idExterno);

        mockMvc.perform(post("/api/webhooks/gateway")
                        .header("Stripe-Signature", assinar(corpo))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo))
                .andExpect(status().isOk());

        // 2. O pedido ja esta pago e o evento ja esta guardado — na mesma
        //    transacao. O estoque ainda NAO baixou: isso e da fila.
        assertThat(pedidos.porId(pedido.getId()).orElseThrow().getStatus()).isEqualTo(StatusPedido.PAGO);
        assertThat(outbox.contarPendentes()).isEqualTo(pendentesAntes + 1);
        assertThat(produtos.porId(produto.getId()).orElseThrow().getEstoqueReservado())
                .as("no momento do webhook, as unidades ainda estao reservadas")
                .isEqualTo(2);

        // 3. O worker publica.
        worker.despacharLote();

        // 4. Os consumidores agem.
        esperarAte("o estoque ser baixado",
                () -> produtos.porId(produto.getId()).orElseThrow().getEstoqueReservado() == 0);
        esperarAte("o cliente ser notificado",
                () -> !notificacoes.doPedido(pedido.getId()).isEmpty());

        Produto depois = produtos.porId(produto.getId()).orElseThrow();
        assertThat(depois.getEstoqueDisponivel()).isEqualTo(18);
        assertThat(depois.getEstoqueTotal()).isEqualTo(18);
        assertThat(notificacoes.doPedido(pedido.getId())).hasSize(1);
    }
}
