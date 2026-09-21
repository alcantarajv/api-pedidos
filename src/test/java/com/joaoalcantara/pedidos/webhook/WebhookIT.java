package com.joaoalcantara.pedidos.webhook;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import com.joaoalcantara.pedidos.comum.TesteDeIntegracao;
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
 * O webhook de ponta a ponta: assinatura, idempotencia e efeito no dominio.
 *
 * <p>O teste {@code mesmoEventoDuasVezes} e o argumento central desta etapa.</p>
 */
@TesteDeIntegracao
@AutoConfigureMockMvc
class WebhookIT {

    private static final String SEGREDO = "whsec_segredo_falso_de_teste";
    private static final String ROTA = "/api/webhooks/gateway";

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
    private JdbcTemplate jdbc;

    /** Cria pedido + cobranca local, prontos para receber o evento. */
    private Cenario novoCenario(int estoque, int quantidade) {
        Usuario cliente = usuarios.salvar(new Usuario("Cliente", "cliente-" + UUID.randomUUID() + "@exemplo.com",
                "$2a$10$hashfalsoparateste000000000000000000000000000000000000", Papel.CLIENTE, Instant.now()));
        Produto produto = produtos.salvar(
                new Produto("Produto " + UUID.randomUUID(), "d", new BigDecimal("100.00"), estoque));

        var resultado = pedidoServico.criar(
                new UsuarioAutenticado(cliente.getId(), cliente.getNome(), cliente.getPapel()),
                UUID.randomUUID().toString(),
                new PedidoRequisicao(java.util.List.of(new ItemRequisicao(produto.getId(), quantidade))));

        Pedido pedido = resultado.pedido();
        String idExterno = "pi_" + UUID.randomUUID();
        pagamentos.salvar(new Pagamento(pedido, idExterno, pedido.getValorTotal(),
                StatusPagamento.PENDENTE, Instant.now()));

        return new Cenario(pedido.getId(), produto.getId(), idExterno);
    }

    private record Cenario(Long pedidoId, Long produtoId, String idExterno) {
    }

    private static String corpoDoEvento(String idDoEvento, String tipo, String idDaCobranca) {
        return """
                {"id":"%s","object":"event","type":"%s","data":{"object":{"id":"%s","object":"payment_intent","status":"succeeded"}}}"""
                .formatted(idDoEvento, tipo, idDaCobranca);
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

    private void entregar(String corpo, org.springframework.test.web.servlet.ResultMatcher esperado) throws Exception {
        mockMvc.perform(post(ROTA)
                        .header("Stripe-Signature", assinar(corpo))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo))
                .andExpect(esperado);
    }

    @Test
    @DisplayName("pagamento aprovado confirma o pedido e grava o evento no outbox, na mesma transacao")
    void pagamentoAprovado() throws Exception {
        Cenario cenario = novoCenario(10, 3);
        Integer pendentesAntes = jdbc.queryForObject("SELECT count(*) FROM outbox WHERE publicado_em IS NULL", Integer.class);

        entregar(corpoDoEvento("evt_" + UUID.randomUUID(), "payment_intent.succeeded", cenario.idExterno()),
                status().isOk());

        assertThat(pedidos.porId(cenario.pedidoId()).orElseThrow().getStatus()).isEqualTo(StatusPedido.PAGO);
        assertThat(pagamentos.porIdExterno(cenario.idExterno()).orElseThrow().getStatus())
                .isEqualTo(StatusPagamento.APROVADO);

        assertThat(jdbc.queryForObject("SELECT count(*) FROM outbox WHERE publicado_em IS NULL", Integer.class))
                .as("o evento nasceu junto com a confirmacao do pedido")
                .isEqualTo(pendentesAntes + 1);

        assertThat(produtos.porId(cenario.produtoId()).orElseThrow().getEstoqueReservado())
                .as("a baixa do estoque e trabalho do consumidor da fila, nao do webhook")
                .isEqualTo(3);
    }

    @Test
    @DisplayName("o MESMO evento entregue duas vezes produz um unico efeito")
    void mesmoEventoDuasVezes() throws Exception {
        Cenario cenario = novoCenario(10, 3);
        String corpo = corpoDoEvento("evt_repetido_" + UUID.randomUUID(),
                "payment_intent.succeeded", cenario.idExterno());

        // O gateway entrega, nao recebe a confirmacao a tempo, e entrega de novo.
        entregar(corpo, status().isOk());
        entregar(corpo, status().isOk());
        entregar(corpo, status().isOk());

        // O pedido foi confirmado uma vez: a segunda transicao seria invalida e
        // teria derrubado a requisicao se o evento tivesse sido reprocessado.
        assertThat(pedidos.porId(cenario.pedidoId()).orElseThrow().getStatus()).isEqualTo(StatusPedido.PAGO);

        // A prova que interessa: o efeito aconteceu uma vez so.
        Integer registros = jdbc.queryForObject(
                "SELECT count(*) FROM eventos_processados WHERE id_externo LIKE 'evt_repetido_%'",
                Integer.class);
        assertThat(registros).as("o evento foi registrado uma unica vez").isEqualTo(1);

        Integer eventosNoOutbox = jdbc.queryForObject(
                "SELECT count(*) FROM outbox WHERE agregado_id = ?", Integer.class,
                String.valueOf(cenario.pedidoId()));
        assertThat(eventosNoOutbox)
                .as("um unico pedido.pago foi gerado: tres entregas, um evento")
                .isEqualTo(1);
    }

    @Test
    @DisplayName("assinatura invalida e recusada com 401, sem tocar no pedido")
    void assinaturaInvalida() throws Exception {
        Cenario cenario = novoCenario(10, 2);
        String corpo = corpoDoEvento("evt_" + UUID.randomUUID(), "payment_intent.succeeded", cenario.idExterno());

        mockMvc.perform(post(ROTA)
                        .header("Stripe-Signature", "t=" + Instant.now().getEpochSecond() + ",v1=0000")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.type").value("https://api.pedidos.dev/erros/assinatura-invalida"));

        assertThat(pedidos.porId(cenario.pedidoId()).orElseThrow().getStatus())
                .isEqualTo(StatusPedido.AGUARDANDO_PAGAMENTO);
        assertThat(produtos.porId(cenario.produtoId()).orElseThrow().getEstoqueReservado()).isEqualTo(2);
    }

    @Test
    @DisplayName("sem cabecalho de assinatura tambem e 401")
    void semAssinatura() throws Exception {
        Cenario cenario = novoCenario(5, 1);

        mockMvc.perform(post(ROTA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoDoEvento("evt_x", "payment_intent.succeeded", cenario.idExterno())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("evento de cobranca desconhecida responde 200 e nao faz nada")
    void cobrancaDesconhecida() throws Exception {
        entregar(corpoDoEvento("evt_" + UUID.randomUUID(), "payment_intent.succeeded", "pi_de_outro_ambiente"),
                status().isOk());
    }

    @Test
    @DisplayName("tipo de evento sem tratamento responde 200 e nao muda nada")
    void tipoIgnorado() throws Exception {
        Cenario cenario = novoCenario(10, 2);

        entregar(corpoDoEvento("evt_" + UUID.randomUUID(), "charge.updated", cenario.idExterno()),
                status().isOk());

        assertThat(pedidos.porId(cenario.pedidoId()).orElseThrow().getStatus())
                .isEqualTo(StatusPedido.AGUARDANDO_PAGAMENTO);
    }

    @Test
    @DisplayName("pagamento recusado marca a cobranca, mas nao encerra o pedido")
    void pagamentoRecusado() throws Exception {
        Cenario cenario = novoCenario(10, 2);

        entregar(corpoDoEvento("evt_" + UUID.randomUUID(), "payment_intent.payment_failed", cenario.idExterno()),
                status().isOk());

        assertThat(pagamentos.porIdExterno(cenario.idExterno()).orElseThrow().getStatus())
                .isEqualTo(StatusPagamento.RECUSADO);
        // O cliente ainda pode tentar outro cartao.
        assertThat(pedidos.porId(cenario.pedidoId()).orElseThrow().getStatus())
                .isEqualTo(StatusPedido.AGUARDANDO_PAGAMENTO);
        assertThat(produtos.porId(cenario.produtoId()).orElseThrow().getEstoqueReservado())
                .as("a reserva continua de pe enquanto o pedido vive")
                .isEqualTo(2);
    }

    @Test
    @DisplayName("corpo que nao e JSON valido responde 400")
    void corpoInvalido() throws Exception {
        String corpo = "isto nao e json";

        mockMvc.perform(post(ROTA)
                        .header("Stripe-Signature", assinar(corpo))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("https://api.pedidos.dev/erros/evento-mal-formado"));
    }

    @Test
    @DisplayName("aprovacao atrasada de um pedido ja cancelado nao o ressuscita")
    void aprovacaoDepoisDoCancelamento() throws Exception {
        Cenario cenario = novoCenario(10, 2);
        Usuario admin = usuarios.salvar(new Usuario("Admin", "admin-" + UUID.randomUUID() + "@exemplo.com",
                "$2a$10$hashfalsoparateste000000000000000000000000000000000000", Papel.ADMIN, Instant.now()));
        pedidoServico.cancelar(new UsuarioAutenticado(admin.getId(), admin.getNome(), admin.getPapel()),
                cenario.pedidoId());

        String corpo = corpoDoEvento("evt_" + UUID.randomUUID(), "payment_intent.succeeded", cenario.idExterno());

        // A transicao invalida derruba a transacao: o gateway recebe erro e vai
        // reentregar, o que e o certo — um pagamento aprovado para um pedido
        // cancelado e um caso para reembolso, decidido por gente, nao um evento
        // para engolir em silencio.
        mockMvc.perform(post(ROTA)
                        .header("Stripe-Signature", assinar(corpo))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo))
                .andExpect(status().isUnprocessableEntity());

        assertThat(pedidos.porId(cenario.pedidoId()).orElseThrow().getStatus())
                .isEqualTo(StatusPedido.CANCELADO);
        assertThat(produtos.porId(cenario.produtoId()).orElseThrow().getEstoqueDisponivel())
                .as("o estoque devolvido pelo cancelamento continua devolvido")
                .isEqualTo(10);
    }
}
