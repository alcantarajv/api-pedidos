package com.joaoalcantara.pedidos.pagamento;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.joaoalcantara.pedidos.comum.GatewayFalso;
import com.joaoalcantara.pedidos.comum.TesteDeIntegracao;
import com.joaoalcantara.pedidos.pagamento.dominio.PagamentoRepositorio;
import com.joaoalcantara.pedidos.pedido.dominio.PedidoRepositorio;
import com.joaoalcantara.pedidos.pedido.dominio.StatusPedido;
import com.joaoalcantara.pedidos.produto.dominio.Produto;
import com.joaoalcantara.pedidos.produto.dominio.ProdutoRepositorio;
import com.joaoalcantara.pedidos.seguranca.ServicoDeToken;
import com.joaoalcantara.pedidos.usuario.dominio.Papel;
import com.joaoalcantara.pedidos.usuario.dominio.Usuario;
import com.joaoalcantara.pedidos.usuario.dominio.UsuarioRepositorio;

/** Cobranca pela API, com o gateway substituido por um servidor falso. */
@TesteDeIntegracao
@AutoConfigureMockMvc
class PagamentoIT {

    private static final GatewayFalso gatewayFalso = new GatewayFalso();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProdutoRepositorio produtos;

    @Autowired
    private UsuarioRepositorio usuarios;

    @Autowired
    private PedidoRepositorio pedidos;

    @Autowired
    private PagamentoRepositorio pagamentos;

    @Autowired
    private PasswordEncoder codificador;

    @Autowired
    private ServicoDeToken servicoDeToken;

    @DynamicPropertySource
    static void apontarParaOGatewayFalso(DynamicPropertyRegistry propriedades) {
        gatewayFalso.iniciar(propriedades);
    }

    @AfterAll
    static void desligar() {
        gatewayFalso.parar();
    }

    @BeforeEach
    void estubePadrao() {
        gatewayFalso.limpar();
        gatewayFalso.servidor().stubFor(WireMock.post(WireMock.urlEqualTo("/v1/payment_intents"))
                .willReturn(WireMock.aResponse().withStatus(200)
                        .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .withBody(GatewayFalso.intentJson("pi_" + UUID.randomUUID(), "requires_payment_method"))));
    }

    private Usuario novoUsuario(Papel papel) {
        return usuarios.salvar(new Usuario("Fulano", papel.name().toLowerCase() + "-" + UUID.randomUUID() + "@exemplo.com",
                codificador.encode("senha-bem-secreta"), papel, Instant.now()));
    }

    private String tokenDe(Usuario usuario) {
        return servicoDeToken.emitirPara(usuario).token();
    }

    private String criarPedido(String token) throws Exception {
        Produto produto = produtos.salvar(
                new Produto("Produto " + UUID.randomUUID(), "d", new BigDecimal("150.00"), 10));

        String corpo = mockMvc.perform(post("/api/pedidos")
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"itens":[{"produtoId":%d,"quantidade":2}]}
                                """.formatted(produto.getId())))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return corpo.replaceAll(".*?\"id\"\\s*:\\s*(\\d+).*", "$1");
    }

    @Test
    @DisplayName("cria a cobranca e devolve o segredo para o front-end concluir")
    void criaCobranca() throws Exception {
        Usuario cliente = novoUsuario(Papel.CLIENTE);
        String token = tokenDe(cliente);
        String pedidoId = criarPedido(token);

        mockMvc.perform(post("/api/pedidos/" + pedidoId + "/pagamento")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDENTE"))
                .andExpect(jsonPath("$.valor").value(300.00))
                .andExpect(jsonPath("$.idExterno").exists())
                .andExpect(jsonPath("$.segredoDoCliente").exists());

        assertThat(pagamentos.porPedido(Long.parseLong(pedidoId))).isPresent();

        // 300,00 em centavos.
        gatewayFalso.servidor().verify(WireMock.postRequestedFor(WireMock.urlEqualTo("/v1/payment_intents"))
                .withRequestBody(WireMock.containing("amount=30000")));
    }

    @Test
    @DisplayName("cobrar duas vezes o mesmo pedido nao cria duas cobrancas no gateway")
    void cobrarDuasVezes() throws Exception {
        Usuario cliente = novoUsuario(Papel.CLIENTE);
        String token = tokenDe(cliente);
        String pedidoId = criarPedido(token);

        gatewayFalso.servidor().stubFor(WireMock.get(WireMock.urlPathMatching("/v1/payment_intents/.*"))
                .willReturn(WireMock.aResponse().withStatus(200)
                        .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .withBody(GatewayFalso.intentJson("pi_existente", "requires_payment_method"))));

        for (int i = 0; i < 2; i++) {
            mockMvc.perform(post("/api/pedidos/" + pedidoId + "/pagamento")
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isCreated());
        }

        gatewayFalso.servidor().verify(1, WireMock.postRequestedFor(WireMock.urlEqualTo("/v1/payment_intents")));
    }

    @Test
    @DisplayName("a consulta atualiza o status local com o que o gateway diz")
    void consultaAtualizaStatus() throws Exception {
        Usuario cliente = novoUsuario(Papel.CLIENTE);
        String token = tokenDe(cliente);
        String pedidoId = criarPedido(token);

        String corpoCriacao = mockMvc.perform(post("/api/pedidos/" + pedidoId + "/pagamento")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String idExterno = corpoCriacao.replaceAll(".*\"idExterno\"\\s*:\\s*\"([^\"]+)\".*", "$1");

        gatewayFalso.servidor().stubFor(WireMock.get(WireMock.urlPathMatching("/v1/payment_intents/.*"))
                .willReturn(WireMock.aResponse().withStatus(200)
                        .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .withBody(GatewayFalso.intentJson(idExterno, "succeeded"))));

        mockMvc.perform(get("/api/pedidos/" + pedidoId + "/pagamento")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APROVADO"))
                // O segredo nao volta em consulta: so quem esta concluindo o
                // pagamento precisa dele.
                .andExpect(jsonPath("$.segredoDoCliente").doesNotExist());

        // A consulta NAO confirma o pedido: isso e trabalho exclusivo do webhook.
        assertThat(pedidos.porId(Long.parseLong(pedidoId)).orElseThrow().getStatus())
                .isEqualTo(StatusPedido.AGUARDANDO_PAGAMENTO);
    }

    @Test
    @DisplayName("gateway fora do ar responde 502, nao 500")
    void gatewayForaDoAr() throws Exception {
        Usuario cliente = novoUsuario(Papel.CLIENTE);
        String token = tokenDe(cliente);
        String pedidoId = criarPedido(token);

        gatewayFalso.servidor().stubFor(WireMock.post(WireMock.urlEqualTo("/v1/payment_intents"))
                .willReturn(WireMock.aResponse().withStatus(503)));

        mockMvc.perform(post("/api/pedidos/" + pedidoId + "/pagamento")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.type").value("https://api.pedidos.dev/erros/gateway-indisponivel"));

        assertThat(pagamentos.porPedido(Long.parseLong(pedidoId)))
                .as("cobranca que falhou no gateway nao deixa registro local orfao")
                .isEmpty();
    }

    @Test
    @DisplayName("pedido cancelado nao aceita cobranca")
    void pedidoCanceladoNaoCobra() throws Exception {
        Usuario cliente = novoUsuario(Papel.CLIENTE);
        String token = tokenDe(cliente);
        String pedidoId = criarPedido(token);

        mockMvc.perform(post("/api/pedidos/" + pedidoId + "/cancelamento")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/pedidos/" + pedidoId + "/pagamento")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.type").value("https://api.pedidos.dev/erros/pedido-nao-cobravel"));
    }

    @Test
    @DisplayName("cliente nao cobra o pedido de outro: 404")
    void pedidoDeOutroCliente() throws Exception {
        String pedidoId = criarPedido(tokenDe(novoUsuario(Papel.CLIENTE)));

        mockMvc.perform(post("/api/pedidos/" + pedidoId + "/pagamento")
                        .header("Authorization", "Bearer " + tokenDe(novoUsuario(Papel.CLIENTE))))
                .andExpect(status().isNotFound());
    }
}
