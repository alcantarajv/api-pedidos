package com.joaoalcantara.pedidos.pagamento.infra;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.joaoalcantara.pedidos.comum.GatewayFalso;
import com.joaoalcantara.pedidos.comum.TesteDeIntegracao;
import com.joaoalcantara.pedidos.comum.erro.GatewayIndisponivelException;
import com.joaoalcantara.pedidos.pagamento.dominio.GatewayDePagamento;
import com.joaoalcantara.pedidos.pagamento.dominio.StatusPagamento;

/**
 * O adaptador do Stripe contra um servidor HTTP falso.
 *
 * <p>Verifica os dois lados da conversa: o que mandamos (corpo, cabecalhos,
 * rota) e o que fazemos com o que volta (traducao de status, erro, timeout).</p>
 */
@TesteDeIntegracao
class GatewayStripeIT {

    private static final GatewayFalso gatewayFalso = new GatewayFalso();

    @Autowired
    private GatewayDePagamento gateway;

    @DynamicPropertySource
    static void apontarParaOGatewayFalso(DynamicPropertyRegistry propriedades) {
        gatewayFalso.iniciar(propriedades);
    }

    @AfterAll
    static void desligar() {
        gatewayFalso.parar();
    }

    @BeforeEach
    void limparEstubes() {
        gatewayFalso.limpar();
    }

    @Test
    @DisplayName("a criacao manda valor em centavos, moeda, metadata e a chave de idempotencia")
    void criaCobrancaComOsCamposCertos() {
        gatewayFalso.servidor().stubFor(post(urlEqualTo("/v1/payment_intents"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .withBody(GatewayFalso.intentJson("pi_teste_1", "requires_payment_method"))));

        var cobranca = gateway.criar("42", new BigDecimal("300.00"), "chave-abc");

        assertThat(cobranca.idExterno()).isEqualTo("pi_teste_1");
        assertThat(cobranca.status()).isEqualTo(StatusPagamento.PENDENTE);
        assertThat(cobranca.segredoDoCliente()).isEqualTo("pi_teste_1_secret_abc123");

        gatewayFalso.servidor().verify(postRequestedFor(urlEqualTo("/v1/payment_intents"))
                .withHeader(HttpHeaders.AUTHORIZATION, equalTo("Bearer sk_test_chave_falsa_de_teste"))
                .withHeader("Idempotency-Key", equalTo("chave-abc"))
                .withHeader(HttpHeaders.CONTENT_TYPE, containing(MediaType.APPLICATION_FORM_URLENCODED_VALUE))
                // R$ 300,00 sao 30000 centavos. Mandar "300" cobraria tres reais.
                .withRequestBody(containing("amount=30000"))
                .withRequestBody(containing("currency=brl"))
                .withRequestBody(containing("pedido_id"))
                .withRequestBody(containing("42")));
    }

    @Test
    @DisplayName("valor com centavos quebrados vira o inteiro certo")
    void valorComCentavos() {
        gatewayFalso.servidor().stubFor(post(urlEqualTo("/v1/payment_intents"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .withBody(GatewayFalso.intentJson("pi_teste_2", "processing"))));

        gateway.criar("7", new BigDecimal("1899.99"), "chave-xyz");

        gatewayFalso.servidor().verify(postRequestedFor(urlEqualTo("/v1/payment_intents"))
                .withRequestBody(containing("amount=189999")));
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({
            "succeeded,APROVADO",
            "canceled,CANCELADO",
            "requires_payment_method,PENDENTE",
            "requires_confirmation,PENDENTE",
            "requires_action,PENDENTE",
            "processing,PENDENTE",
            "um_status_que_o_stripe_ainda_vai_inventar,PENDENTE"
    })
    @DisplayName("o vocabulario do Stripe e traduzido para o do dominio")
    void traduzStatus(String statusDoStripe, StatusPagamento esperado) {
        gatewayFalso.servidor().stubFor(get(urlPathEqualTo("/v1/payment_intents/pi_consulta"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .withBody(GatewayFalso.intentJson("pi_consulta", statusDoStripe))));

        assertThat(gateway.consultar("pi_consulta").status()).isEqualTo(esperado);

        gatewayFalso.servidor().verify(getRequestedFor(urlPathEqualTo("/v1/payment_intents/pi_consulta"))
                .withHeader(HttpHeaders.AUTHORIZATION, equalTo("Bearer sk_test_chave_falsa_de_teste")));
    }

    @Test
    @DisplayName("erro do gateway vira excecao de dominio, nao vaza o corpo da resposta")
    void erroDoGateway() {
        gatewayFalso.servidor().stubFor(post(urlEqualTo("/v1/payment_intents"))
                .willReturn(aResponse().withStatus(401)
                        .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .withBody("""
                                {"error":{"message":"Invalid API Key provided: sk_test_***"}}
                                """)));

        assertThatThrownBy(() -> gateway.criar("42", new BigDecimal("10.00"), "chave-1"))
                .isInstanceOf(GatewayIndisponivelException.class)
                .hasMessageContaining("401")
                // O detalhe do fornecedor fica no log, nao na excecao que o
                // cliente vai ver.
                .hasMessageNotContaining("Invalid API Key");
    }

    @Test
    @DisplayName("gateway que nao responde a tempo vira excecao de dominio, nao trava a requisicao")
    void timeout() {
        // O perfil de teste usa timeout de leitura de 1s; o dublê demora 3s.
        gatewayFalso.servidor().stubFor(post(urlEqualTo("/v1/payment_intents"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .withBody(GatewayFalso.intentJson("pi_lento", "processing"))
                        .withFixedDelay(3000)));

        long comeco = System.currentTimeMillis();

        assertThatThrownBy(() -> gateway.criar("42", new BigDecimal("10.00"), "chave-2"))
                .isInstanceOf(GatewayIndisponivelException.class)
                .hasMessageContaining("nao respondeu");

        assertThat(System.currentTimeMillis() - comeco)
                .as("a chamada desiste no timeout configurado, em vez de esperar o gateway")
                .isLessThan(3000);
    }
}
