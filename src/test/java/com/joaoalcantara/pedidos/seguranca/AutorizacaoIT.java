package com.joaoalcantara.pedidos.seguranca;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
// Spring Boot 4: a anotacao mudou de pacote. Era
// org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc.
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;

import com.joaoalcantara.pedidos.comum.TesteDeIntegracao;
import com.joaoalcantara.pedidos.usuario.dominio.Papel;
import com.joaoalcantara.pedidos.usuario.dominio.Usuario;
import com.joaoalcantara.pedidos.usuario.dominio.UsuarioRepositorio;

/**
 * Fluxo completo de autenticacao e autorizacao pela cadeia de filtros real.
 *
 * <p>Nao ha atalho de contexto aqui — nada de {@code @WithMockUser}. Cada
 * requisicao carrega um token emitido pelo proprio sistema, porque o que este
 * teste precisa provar inclui o filtro JWT, a configuracao de rotas e o formato
 * do erro. Um usuario injetado direto no contexto pularia justamente as partes
 * que podem estar erradas.</p>
 */
@TesteDeIntegracao
@AutoConfigureMockMvc
class AutorizacaoIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UsuarioRepositorio usuarios;

    @Autowired
    private PasswordEncoder codificador;

    private static final String SENHA = "senha-bem-secreta";

    private String emailUnico(String prefixo) {
        return prefixo + "-" + UUID.randomUUID() + "@exemplo.com";
    }

    private String tokenDeClienteRegistrado() throws Exception {
        String email = emailUnico("cliente");
        mockMvc.perform(post("/api/auth/registrar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nome":"Cliente","email":"%s","senha":"%s"}
                                """.formatted(email, SENHA)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.papel").value("CLIENTE"));

        return login(email);
    }

    private String tokenDeAdmin() throws Exception {
        String email = emailUnico("admin");
        usuarios.salvar(new Usuario("Admin", email, codificador.encode(SENHA), Papel.ADMIN, Instant.now()));
        return login(email);
    }

    private String login(String email) throws Exception {
        String corpo = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","senha":"%s"}
                                """.formatted(email, SENHA)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        return corpo.replaceAll(".*\"token\"\\s*:\\s*\"([^\"]+)\".*", "$1");
    }

    private String produtoJson() {
        return """
                {"nome":"Produto %s","descricao":"d","preco":10.00,"estoqueInicial":5}
                """.formatted(UUID.randomUUID());
    }

    @Test
    @DisplayName("registro devolve 201 e o papel CLIENTE, nunca ADMIN")
    void registroPublicoCriaCliente() throws Exception {
        assertThat(tokenDeClienteRegistrado()).isNotBlank();
    }

    @Test
    @DisplayName("senha errada responde 401 em problem details, sem dizer o que falhou")
    void senhaErradaResponde401() throws Exception {
        String email = emailUnico("cliente");
        mockMvc.perform(post("/api/auth/registrar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nome":"Cliente","email":"%s","senha":"%s"}
                                """.formatted(email, SENHA)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","senha":"senha-errada"}
                                """.formatted(email)))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("E-mail ou senha incorretos"));
    }

    @Test
    @DisplayName("o catalogo e legivel sem token")
    void leituraPublica() throws Exception {
        mockMvc.perform(get("/api/produtos")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("criar produto sem token responde 401 em problem details")
    void escritaSemTokenResponde401() throws Exception {
        mockMvc.perform(post("/api/produtos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(produtoJson()))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("https://api.pedidos.dev/erros/nao-autenticado"));
    }

    @Test
    @DisplayName("cliente autenticado nao administra o catalogo: 403, nao 401")
    void clienteNaoAdministra() throws Exception {
        String token = tokenDeClienteRegistrado();

        mockMvc.perform(post("/api/produtos")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(produtoJson()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value("https://api.pedidos.dev/erros/acesso-negado"));
    }

    @Test
    @DisplayName("admin cria produto")
    void adminAdministra() throws Exception {
        mockMvc.perform(post("/api/produtos")
                        .header("Authorization", "Bearer " + tokenDeAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(produtoJson()))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("token invalido nao autentica, e a rota publica continua respondendo")
    void tokenInvalido() throws Exception {
        mockMvc.perform(get("/api/produtos").header("Authorization", "Bearer isto-nao-e-um-jwt"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/produtos")
                        .header("Authorization", "Bearer isto-nao-e-um-jwt")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(produtoJson()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("o estoque reservado so aparece para ADMIN")
    void visaoPublicaEscondeEstoqueReservado() throws Exception {
        String tokenAdmin = tokenDeAdmin();
        String criado = mockMvc.perform(post("/api/produtos")
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(produtoJson()))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String id = criado.replaceAll(".*\"id\"\\s*:\\s*(\\d+).*", "$1");

        mockMvc.perform(get("/api/produtos/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estoqueDisponivel").value(5))
                .andExpect(jsonPath("$.estoqueReservado").doesNotExist())
                .andExpect(jsonPath("$.estoqueTotal").doesNotExist());

        mockMvc.perform(get("/api/produtos/" + id).header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estoqueReservado").value(0))
                .andExpect(jsonPath("$.estoqueTotal").value(5));
    }

    @Test
    @DisplayName("/api/auth/eu identifica o dono do token")
    void euIdentificaODono() throws Exception {
        mockMvc.perform(get("/api/auth/eu").header("Authorization", "Bearer " + tokenDeClienteRegistrado()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.papel").value("CLIENTE"));
    }
}
