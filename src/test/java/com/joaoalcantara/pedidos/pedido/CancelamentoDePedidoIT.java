package com.joaoalcantara.pedidos.pedido;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import com.joaoalcantara.pedidos.comum.TesteDeIntegracao;
import com.joaoalcantara.pedidos.produto.dominio.Produto;
import com.joaoalcantara.pedidos.produto.dominio.ProdutoRepositorio;
import com.joaoalcantara.pedidos.seguranca.ServicoDeToken;
import com.joaoalcantara.pedidos.usuario.dominio.Papel;
import com.joaoalcantara.pedidos.usuario.dominio.Usuario;
import com.joaoalcantara.pedidos.usuario.dominio.UsuarioRepositorio;

/** Cancelamento pela API: transicao de estado e devolucao do estoque juntas. */
@TesteDeIntegracao
@AutoConfigureMockMvc
class CancelamentoDePedidoIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProdutoRepositorio produtos;

    @Autowired
    private UsuarioRepositorio usuarios;

    @Autowired
    private PasswordEncoder codificador;

    @Autowired
    private ServicoDeToken servicoDeToken;

    private Usuario novoUsuario(Papel papel) {
        return usuarios.salvar(new Usuario("Fulano", papel.name().toLowerCase() + "-" + UUID.randomUUID() + "@exemplo.com",
                codificador.encode("senha-bem-secreta"), papel, Instant.now()));
    }

    private String tokenDe(Usuario usuario) {
        return servicoDeToken.emitirPara(usuario).token();
    }

    private Produto novoProduto(int estoque) {
        return produtos.salvar(new Produto("Produto " + UUID.randomUUID(), "d", new BigDecimal("100.00"), estoque));
    }

    private String criarPedido(String token, Long produtoId, int quantidade) throws Exception {
        String corpo = mockMvc.perform(post("/api/pedidos")
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"itens":[{"produtoId":%d,"quantidade":%d}]}
                                """.formatted(produtoId, quantidade)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return corpo.replaceAll(".*?\"id\"\\s*:\\s*(\\d+).*", "$1");
    }

    @Test
    @DisplayName("cancelar devolve ao catalogo o estoque que o pedido segurava")
    void cancelarDevolveEstoque() throws Exception {
        Usuario cliente = novoUsuario(Papel.CLIENTE);
        String token = tokenDe(cliente);
        Produto produto = novoProduto(10);
        String pedidoId = criarPedido(token, produto.getId(), 4);

        assertThat(produtos.porId(produto.getId()).orElseThrow().getEstoqueDisponivel()).isEqualTo(6);

        mockMvc.perform(post("/api/pedidos/" + pedidoId + "/cancelamento")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELADO"));

        Produto depois = produtos.porId(produto.getId()).orElseThrow();
        assertThat(depois.getEstoqueDisponivel()).isEqualTo(10);
        assertThat(depois.getEstoqueReservado()).isZero();
    }

    @Test
    @DisplayName("cancelar duas vezes: a segunda e recusada e o estoque nao volta em dobro")
    void cancelarDuasVezes() throws Exception {
        Usuario cliente = novoUsuario(Papel.CLIENTE);
        String token = tokenDe(cliente);
        Produto produto = novoProduto(10);
        String pedidoId = criarPedido(token, produto.getId(), 3);

        mockMvc.perform(post("/api/pedidos/" + pedidoId + "/cancelamento")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/pedidos/" + pedidoId + "/cancelamento")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.type").value("https://api.pedidos.dev/erros/transicao-invalida"));

        Produto depois = produtos.porId(produto.getId()).orElseThrow();
        assertThat(depois.getEstoqueDisponivel())
                .as("o estoque volta uma vez so, nao uma por tentativa de cancelamento")
                .isEqualTo(10);
    }

    @Test
    @DisplayName("cliente nao cancela pedido de outro: 404, nao 403")
    void naoCancelaPedidoDeOutro() throws Exception {
        Produto produto = novoProduto(10);
        String pedidoId = criarPedido(tokenDe(novoUsuario(Papel.CLIENTE)), produto.getId(), 2);

        mockMvc.perform(post("/api/pedidos/" + pedidoId + "/cancelamento")
                        .header("Authorization", "Bearer " + tokenDe(novoUsuario(Papel.CLIENTE))))
                .andExpect(status().isNotFound());

        assertThat(produtos.porId(produto.getId()).orElseThrow().getEstoqueReservado())
                .as("uma tentativa recusada nao pode mexer no estoque")
                .isEqualTo(2);
    }

    @Test
    @DisplayName("admin cancela o pedido de qualquer cliente")
    void adminCancela() throws Exception {
        Produto produto = novoProduto(10);
        String pedidoId = criarPedido(tokenDe(novoUsuario(Papel.CLIENTE)), produto.getId(), 2);

        mockMvc.perform(post("/api/pedidos/" + pedidoId + "/cancelamento")
                        .header("Authorization", "Bearer " + tokenDe(novoUsuario(Papel.ADMIN))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELADO"));

        assertThat(produtos.porId(produto.getId()).orElseThrow().getEstoqueDisponivel()).isEqualTo(10);
    }
}
