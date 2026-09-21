package com.joaoalcantara.pedidos.seguranca;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.joaoalcantara.pedidos.usuario.dominio.Papel;
import com.joaoalcantara.pedidos.usuario.dominio.Usuario;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;

/**
 * Emissao e validacao de token, com o tempo sob controle.
 *
 * <p>O {@link Clock} injetado e o que torna este teste possivel: provar que um
 * token expirado e recusado exige avancar o relogio, e nao esperar duas horas.</p>
 */
class ServicoDeTokenTest {

    private static final Instant AGORA = Instant.parse("2026-09-21T12:00:00Z");
    private static final String SEGREDO = "segredo-apenas-para-testes-com-mais-de-32-caracteres";

    private final Usuario cliente = usuarioCom(Papel.CLIENTE);

    private static Usuario usuarioCom(Papel papel) {
        return new Usuario("Joao", "joao@exemplo.com", "hash-irrelevante", papel, AGORA);
    }

    private ServicoDeToken servicoEm(Instant instante) {
        Clock relogio = Clock.fixed(instante, ZoneOffset.UTC);
        return new ServicoDeToken(new PropriedadesJwt(SEGREDO, 120, "pedidos-api"), relogio);
    }

    @Test
    @DisplayName("o token carrega id, nome e papel do usuario")
    void tokenCarregaIdentidade() {
        ServicoDeToken servico = servicoEm(AGORA);

        String token = servico.emitirPara(cliente).token();
        Claims claims = servico.validar(token);

        assertThat(ServicoDeToken.papel(claims)).isEqualTo("CLIENTE");
        assertThat(ServicoDeToken.nome(claims)).isEqualTo("Joao");
        assertThat(claims.getIssuer()).isEqualTo("pedidos-api");
    }

    @Test
    @DisplayName("a expiracao respeita o tempo configurado")
    void expiracaoConfigurada() {
        var emitido = servicoEm(AGORA).emitirPara(cliente);

        assertThat(emitido.expiraEm()).isEqualTo(AGORA.plus(Duration.ofMinutes(120)));
        assertThat(emitido.expiraEmSegundos()).isEqualTo(7200);
    }

    @Test
    @DisplayName("token vencido e recusado")
    void tokenVencido() {
        String token = servicoEm(AGORA).emitirPara(cliente).token();

        // Mesmo segredo, mesmo emissor: o que mudou foi so o relogio.
        ServicoDeToken depoisDoPrazo = servicoEm(AGORA.plus(Duration.ofMinutes(121)));

        assertThatThrownBy(() -> depoisDoPrazo.validar(token)).isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("token assinado com outro segredo e recusado")
    void assinaturaDeOutroSegredo() {
        Clock relogio = Clock.fixed(AGORA, ZoneOffset.UTC);
        ServicoDeToken intruso = new ServicoDeToken(
                new PropriedadesJwt("outro-segredo-de-testes-com-mais-de-32-caracteres", 120, "pedidos-api"), relogio);

        String tokenForjado = intruso.emitirPara(usuarioCom(Papel.ADMIN)).token();

        assertThatThrownBy(() -> servicoEm(AGORA).validar(tokenForjado)).isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("token de outro emissor e recusado")
    void emissorDiferente() {
        Clock relogio = Clock.fixed(AGORA, ZoneOffset.UTC);
        ServicoDeToken outroSistema = new ServicoDeToken(
                new PropriedadesJwt(SEGREDO, 120, "outro-sistema"), relogio);

        String token = outroSistema.emitirPara(cliente).token();

        assertThatThrownBy(() -> servicoEm(AGORA).validar(token)).isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("texto qualquer no lugar do token nao derruba a validacao com erro inesperado")
    void tokenMalFormado() {
        ServicoDeToken servico = servicoEm(AGORA);

        assertThatThrownBy(() -> servico.validar("isto-nao-e-um-jwt"))
                .isInstanceOfAny(JwtException.class, IllegalArgumentException.class);
    }
}
