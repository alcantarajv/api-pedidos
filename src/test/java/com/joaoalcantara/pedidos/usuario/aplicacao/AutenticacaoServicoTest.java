package com.joaoalcantara.pedidos.usuario.aplicacao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.joaoalcantara.pedidos.comum.erro.ConflitoException;
import com.joaoalcantara.pedidos.seguranca.PropriedadesJwt;
import com.joaoalcantara.pedidos.seguranca.ServicoDeToken;
import com.joaoalcantara.pedidos.usuario.api.LoginRequisicao;
import com.joaoalcantara.pedidos.usuario.api.RegistroRequisicao;
import com.joaoalcantara.pedidos.usuario.dominio.Papel;
import com.joaoalcantara.pedidos.usuario.dominio.Usuario;
import com.joaoalcantara.pedidos.usuario.dominio.UsuarioRepositorio;

/** Registro e login. */
class AutenticacaoServicoTest {

    private static final Instant AGORA = Instant.parse("2026-09-21T12:00:00Z");

    private final UsuarioRepositorio repositorio = mock(UsuarioRepositorio.class);
    private final PasswordEncoder codificador = new BCryptPasswordEncoder();
    private final Clock relogio = Clock.fixed(AGORA, ZoneOffset.UTC);
    private final ServicoDeToken servicoDeToken = new ServicoDeToken(
            new PropriedadesJwt("segredo-apenas-para-testes-com-mais-de-32-caracteres", 120, "pedidos-api"), relogio);

    private final AutenticacaoServico servico =
            new AutenticacaoServico(repositorio, codificador, servicoDeToken, relogio);

    @BeforeEach
    void devolveOQueRecebe() {
        when(repositorio.salvar(any(Usuario.class))).thenAnswer(chamada -> chamada.getArgument(0));
        when(repositorio.existeComEmail(anyString())).thenReturn(false);
    }

    private RegistroRequisicao registro(String email) {
        return new RegistroRequisicao("Joao Vitor", email, "senha-bem-secreta");
    }

    @Test
    @DisplayName("registro publico sempre cria CLIENTE")
    void registroCriaCliente() {
        Usuario usuario = servico.registrar(registro("joao@exemplo.com"));

        assertThat(usuario.getPapel()).isEqualTo(Papel.CLIENTE);
        assertThat(usuario.ehAdmin()).isFalse();
    }

    @Test
    @DisplayName("a senha nunca e guardada em claro")
    void senhaEArmazenadaComoHash() {
        Usuario usuario = servico.registrar(registro("joao@exemplo.com"));

        assertThat(usuario.getSenhaHash())
                .isNotEqualTo("senha-bem-secreta")
                .startsWith("$2");
        assertThat(codificador.matches("senha-bem-secreta", usuario.getSenhaHash())).isTrue();
    }

    @Test
    @DisplayName("e-mail e normalizado para minusculas")
    void emailNormalizado() {
        Usuario usuario = servico.registrar(registro("Joao@Exemplo.COM"));

        assertThat(usuario.getEmail()).isEqualTo("joao@exemplo.com");
    }

    @Test
    @DisplayName("e-mail ja cadastrado vira 409, nao erro de banco")
    void emailDuplicado() {
        when(repositorio.existeComEmail("joao@exemplo.com")).thenReturn(true);

        assertThatThrownBy(() -> servico.registrar(registro("joao@exemplo.com")))
                .isInstanceOf(ConflitoException.class);
    }

    @Test
    @DisplayName("login correto devolve token valido para aquele usuario")
    void loginCorreto() {
        Usuario cadastrado = servico.registrar(registro("joao@exemplo.com"));
        when(repositorio.porEmail("joao@exemplo.com")).thenReturn(Optional.of(cadastrado));

        var emitido = servico.autenticar(new LoginRequisicao("joao@exemplo.com", "senha-bem-secreta"));

        assertThat(ServicoDeToken.papel(servicoDeToken.validar(emitido.token()))).isEqualTo("CLIENTE");
        assertThat(emitido.expiraEm()).isEqualTo(AGORA.plusSeconds(7200));
    }

    @Test
    @DisplayName("senha errada e e-mail inexistente produzem o mesmo erro")
    void erroIndistinguivel() {
        Usuario cadastrado = servico.registrar(registro("joao@exemplo.com"));
        when(repositorio.porEmail("joao@exemplo.com")).thenReturn(Optional.of(cadastrado));
        when(repositorio.porEmail("ninguem@exemplo.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servico.autenticar(new LoginRequisicao("joao@exemplo.com", "senha-errada")))
                .isInstanceOf(BadCredentialsException.class);
        assertThatThrownBy(() -> servico.autenticar(new LoginRequisicao("ninguem@exemplo.com", "qualquer-coisa")))
                .isInstanceOf(BadCredentialsException.class);
    }
}
