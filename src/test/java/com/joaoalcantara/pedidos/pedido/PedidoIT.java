package com.joaoalcantara.pedidos.pedido;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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

/** Criacao e consulta de pedidos pela API, com token de verdade. */
@TesteDeIntegracao
@AutoConfigureMockMvc
class PedidoIT {

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

    private Produto novoProduto(String preco, int estoque) {
        return produtos.salvar(new Produto("Produto " + UUID.randomUUID(), "d", new BigDecimal(preco), estoque));
    }

    private String corpoCom(Long produtoId, int quantidade) {
        return """
                {"itens":[{"produtoId":%d,"quantidade":%d}]}
                """.formatted(produtoId, quantidade);
    }

    @Test
    @DisplayName("cria o pedido, congela o preco e reserva o estoque")
    void criaPedidoEReservaEstoque() throws Exception {
        Usuario cliente = novoUsuario(Papel.CLIENTE);
        Produto produto = novoProduto("100.00", 10);

        mockMvc.perform(post("/api/pedidos")
                        .header("Authorization", "Bearer " + tokenDe(cliente))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoCom(produto.getId(), 3)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("AGUARDANDO_PAGAMENTO"))
                .andExpect(jsonPath("$.valorTotal").value(300.00))
                .andExpect(jsonPath("$.itens[0].precoUnitario").value(100.00))
                .andExpect(jsonPath("$.itens[0].quantidade").value(3));

        Produto depois = produtos.porId(produto.getId()).orElseThrow();
        org.assertj.core.api.Assertions.assertThat(depois.getEstoqueDisponivel()).isEqualTo(7);
        org.assertj.core.api.Assertions.assertThat(depois.getEstoqueReservado()).isEqualTo(3);
    }

    @Test
    @DisplayName("reenviar a mesma chave devolve o mesmo pedido com 200, sem reservar de novo")
    void reenvioComAMesmaChave() throws Exception {
        Usuario cliente = novoUsuario(Papel.CLIENTE);
        Produto produto = novoProduto("50.00", 10);
        String token = tokenDe(cliente);
        String chave = UUID.randomUUID().toString();

        String primeira = mockMvc.perform(post("/api/pedidos")
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", chave)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoCom(produto.getId(), 2)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String idCriado = primeira.replaceAll(".*?\"id\"\\s*:\\s*(\\d+).*", "$1");

        mockMvc.perform(post("/api/pedidos")
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", chave)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoCom(produto.getId(), 2)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(Integer.parseInt(idCriado)));

        // O estoque saiu uma vez, nao duas.
        Produto depois = produtos.porId(produto.getId()).orElseThrow();
        org.assertj.core.api.Assertions.assertThat(depois.getEstoqueDisponivel()).isEqualTo(8);
        org.assertj.core.api.Assertions.assertThat(depois.getEstoqueReservado()).isEqualTo(2);
    }

    @Test
    @DisplayName("a mesma chave em contas diferentes cria pedidos diferentes")
    void chaveEhPorUsuario() throws Exception {
        Produto produto = novoProduto("50.00", 10);
        String chave = "chave-compartilhada-" + UUID.randomUUID();

        for (int i = 0; i < 2; i++) {
            mockMvc.perform(post("/api/pedidos")
                            .header("Authorization", "Bearer " + tokenDe(novoUsuario(Papel.CLIENTE)))
                            .header("Idempotency-Key", chave)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(corpoCom(produto.getId(), 1)))
                    .andExpect(status().isCreated());
        }

        Produto depois = produtos.porId(produto.getId()).orElseThrow();
        org.assertj.core.api.Assertions.assertThat(depois.getEstoqueReservado()).isEqualTo(2);
    }

    @Test
    @DisplayName("sem o cabecalho Idempotency-Key a criacao e recusada")
    void semCabecalhoDeIdempotencia() throws Exception {
        Produto produto = novoProduto("50.00", 5);

        mockMvc.perform(post("/api/pedidos")
                        .header("Authorization", "Bearer " + tokenDe(novoUsuario(Papel.CLIENTE)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoCom(produto.getId(), 1)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.type").value("https://api.pedidos.dev/erros/idempotency-key-ausente"));
    }

    @Test
    @DisplayName("estoque insuficiente responde 409")
    void estoqueInsuficiente() throws Exception {
        Produto produto = novoProduto("50.00", 2);

        mockMvc.perform(post("/api/pedidos")
                        .header("Authorization", "Bearer " + tokenDe(novoUsuario(Papel.CLIENTE)))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoCom(produto.getId(), 3)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("https://api.pedidos.dev/erros/estoque-insuficiente"));
    }

    @Test
    @DisplayName("produto inativo nao entra em pedido novo")
    void produtoInativo() throws Exception {
        Produto produto = novoProduto("50.00", 5);
        produto.desativar();
        produtos.salvar(produto);

        mockMvc.perform(post("/api/pedidos")
                        .header("Authorization", "Bearer " + tokenDe(novoUsuario(Papel.CLIENTE)))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoCom(produto.getId(), 1)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.type").value("https://api.pedidos.dev/erros/produto-inativo"));
    }

    @Test
    @DisplayName("o mesmo produto repetido em duas linhas e recusado")
    void itemDuplicado() throws Exception {
        Produto produto = novoProduto("50.00", 10);

        mockMvc.perform(post("/api/pedidos")
                        .header("Authorization", "Bearer " + tokenDe(novoUsuario(Papel.CLIENTE)))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"itens":[{"produtoId":%d,"quantidade":1},{"produtoId":%d,"quantidade":2}]}
                                """.formatted(produto.getId(), produto.getId())))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.type").value("https://api.pedidos.dev/erros/item-duplicado"));
    }

    @Test
    @DisplayName("pedido sem itens e recusado na validacao de formato")
    void pedidoSemItens() throws Exception {
        mockMvc.perform(post("/api/pedidos")
                        .header("Authorization", "Bearer " + tokenDe(novoUsuario(Papel.CLIENTE)))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"itens\":[]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("cliente nao enxerga pedido de outro cliente: 404, nao 403")
    void pedidoDeOutroCliente() throws Exception {
        Produto produto = novoProduto("50.00", 10);
        Usuario dono = novoUsuario(Papel.CLIENTE);

        String criado = mockMvc.perform(post("/api/pedidos")
                        .header("Authorization", "Bearer " + tokenDe(dono))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoCom(produto.getId(), 1)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String id = criado.replaceAll(".*?\"id\"\\s*:\\s*(\\d+).*", "$1");

        mockMvc.perform(get("/api/pedidos/" + id)
                        .header("Authorization", "Bearer " + tokenDe(novoUsuario(Papel.CLIENTE))))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/pedidos/" + id)
                        .header("Authorization", "Bearer " + tokenDe(novoUsuario(Papel.ADMIN))))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("a listagem do cliente traz so os pedidos dele")
    void listagemDoCliente() throws Exception {
        Produto produto = novoProduto("50.00", 10);
        Usuario cliente = novoUsuario(Papel.CLIENTE);
        String token = tokenDe(cliente);

        mockMvc.perform(post("/api/pedidos")
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoCom(produto.getId(), 1)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/pedidos")
                        .header("Authorization", "Bearer " + tokenDe(novoUsuario(Papel.CLIENTE)))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoCom(produto.getId(), 1)))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/pedidos").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].usuarioId").value(cliente.getId()));
    }

    @Test
    @DisplayName("criar pedido sem token responde 401")
    void semToken() throws Exception {
        Produto produto = novoProduto("50.00", 5);

        mockMvc.perform(post("/api/pedidos")
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoCom(produto.getId(), 1)))
                .andExpect(status().isUnauthorized());
    }
}
