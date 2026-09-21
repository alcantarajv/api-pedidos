package com.joaoalcantara.pedidos.webhook.infra;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HexFormat;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.joaoalcantara.pedidos.pagamento.infra.PropriedadesDoGateway;
import com.joaoalcantara.pedidos.webhook.dominio.AssinaturaInvalidaException;

/**
 * A verificacao de assinatura do webhook.
 *
 * <p>Este e o teste de seguranca mais importante do projeto: o endpoint de
 * webhook e publico, e a assinatura e a unica coisa entre ele e qualquer pessoa
 * que descubra a URL. Cada caso abaixo corresponde a uma forma de tentar
 * confirmar um pedido sem ter pagado.</p>
 */
class VerificadorDeAssinaturaTest {

    private static final Instant AGORA = Instant.parse("2026-09-21T12:00:00Z");
    private static final String SEGREDO = "whsec_segredo_falso_de_teste";
    private static final String CORPO = """
            {"id":"evt_1","type":"payment_intent.succeeded","data":{"object":{"id":"pi_1"}}}""";

    private VerificadorDeAssinatura verificadorEm(Instant instante) {
        var propriedades = new PropriedadesDoGateway("http://localhost", "sk_test_x",
                Duration.ofSeconds(3), Duration.ofSeconds(10), SEGREDO, Duration.ofMinutes(5));
        return new VerificadorDeAssinatura(propriedades, Clock.fixed(instante, ZoneOffset.UTC));
    }

    /** Monta o cabecalho como o gateway faria. */
    private static String assinar(String corpo, Instant momento, String segredo) {
        long carimbo = momento.getEpochSecond();
        String hmac = hmacHex(carimbo + "." + corpo, segredo);
        return "t=%d,v1=%s".formatted(carimbo, hmac);
    }

    private static String hmacHex(String conteudo, String segredo) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(segredo.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(conteudo.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    @DisplayName("assinatura legitima e aceita")
    void assinaturaValida() {
        String cabecalho = assinar(CORPO, AGORA, SEGREDO);

        assertThatCode(() -> verificadorEm(AGORA).verificar(CORPO, cabecalho)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("assinatura feita com outro segredo e recusada")
    void segredoErrado() {
        String cabecalho = assinar(CORPO, AGORA, "whsec_segredo_do_atacante");

        assertThatThrownBy(() -> verificadorEm(AGORA).verificar(CORPO, cabecalho))
                .isInstanceOf(AssinaturaInvalidaException.class);
    }

    @Test
    @DisplayName("corpo alterado depois de assinado e recusado")
    void corpoAdulterado() {
        // O cenario real: interceptar um webhook legitimo e trocar o id do
        // PaymentIntent pelo de outra cobranca, para confirmar o proprio pedido.
        String cabecalho = assinar(CORPO, AGORA, SEGREDO);
        String corpoTrocado = CORPO.replace("pi_1", "pi_do_meu_pedido");

        assertThatThrownBy(() -> verificadorEm(AGORA).verificar(corpoTrocado, cabecalho))
                .isInstanceOf(AssinaturaInvalidaException.class);
    }

    @Test
    @DisplayName("assinatura antiga e recusada, mesmo sendo legitima")
    void assinaturaVencida() {
        String cabecalho = assinar(CORPO, AGORA, SEGREDO);

        // Requisicao valida, capturada e reenviada seis minutos depois.
        var verificador = verificadorEm(AGORA.plus(Duration.ofMinutes(6)));

        assertThatThrownBy(() -> verificador.verificar(CORPO, cabecalho))
                .isInstanceOf(AssinaturaInvalidaException.class);
    }

    @Test
    @DisplayName("assinatura dentro da janela de tolerancia e aceita")
    void dentroDaJanela() {
        String cabecalho = assinar(CORPO, AGORA, SEGREDO);

        var verificador = verificadorEm(AGORA.plus(Duration.ofMinutes(4)));

        assertThatCode(() -> verificador.verificar(CORPO, cabecalho)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("cabecalho ausente, vazio ou mal formado e recusado")
    void cabecalhoInvalido() {
        var verificador = verificadorEm(AGORA);

        assertThatThrownBy(() -> verificador.verificar(CORPO, null))
                .isInstanceOf(AssinaturaInvalidaException.class);
        assertThatThrownBy(() -> verificador.verificar(CORPO, "   "))
                .isInstanceOf(AssinaturaInvalidaException.class);
        assertThatThrownBy(() -> verificador.verificar(CORPO, "isto-nao-e-uma-assinatura"))
                .isInstanceOf(AssinaturaInvalidaException.class);
        assertThatThrownBy(() -> verificador.verificar(CORPO, "t=abc,v1=" + hmacHex("x", SEGREDO)))
                .isInstanceOf(AssinaturaInvalidaException.class);
        // Sem o carimbo nao ha o que assinar: o HMAC sozinho nao basta.
        assertThatThrownBy(() -> verificador.verificar(CORPO, "v1=" + hmacHex("x", SEGREDO)))
                .isInstanceOf(AssinaturaInvalidaException.class);
    }

    @Test
    @DisplayName("corpo reserializado nao confere: a assinatura vale sobre os bytes crus")
    void corpoReserializado() {
        String cabecalho = assinar(CORPO, AGORA, SEGREDO);

        // Mesmo JSON semanticamente — so mudaram os espacos, como aconteceria se
        // o corpo tivesse passado por um parser antes da verificacao.
        String mesmoJsonOutroEspacamento = CORPO.replace(",", ", ");

        assertThatThrownBy(() -> verificadorEm(AGORA).verificar(mesmoJsonOutroEspacamento, cabecalho))
                .isInstanceOf(AssinaturaInvalidaException.class);
    }
}
