package com.joaoalcantara.pedidos.produto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.joaoalcantara.pedidos.comum.TesteDeIntegracao;
import com.joaoalcantara.pedidos.pedido.aplicacao.PedidoServico;
import com.joaoalcantara.pedidos.pedido.api.ItemRequisicao;
import com.joaoalcantara.pedidos.pedido.api.PedidoRequisicao;
import com.joaoalcantara.pedidos.produto.dominio.Produto;
import com.joaoalcantara.pedidos.produto.dominio.ProdutoRepositorio;
import com.joaoalcantara.pedidos.seguranca.ServicoDeToken;
import com.joaoalcantara.pedidos.seguranca.UsuarioAutenticado;
import com.joaoalcantara.pedidos.usuario.dominio.Papel;
import com.joaoalcantara.pedidos.usuario.dominio.Usuario;
import com.joaoalcantara.pedidos.usuario.dominio.UsuarioRepositorio;

/**
 * O catalogo pela API, com token de ADMIN.
 *
 * <p>Lacuna encontrada na revisao da suite: as regras do {@code Produto} tinham
 * teste de unidade e a autorizacao tinha teste proprio, mas o caminho HTTP de
 * ajuste de estoque e de mudanca de situacao nunca era exercitado ponta a ponta.
 * Sao justamente os dois endpoints com comportamento menos obvio — quantidade
 * final em vez de delta, e desativar sem cancelar o que ja existe.</p>
 */
@TesteDeIntegracao
@AutoConfigureMockMvc
class CatalogoAdministrativoIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProdutoRepositorio produtos;

    @Autowired
    private UsuarioRepositorio usuarios;

    @Autowired
    private PedidoServico pedidoServico;

    @Autowired
    private ServicoDeToken servicoDeToken;

    private Usuario novoUsuario(Papel papel) {
        return usuarios.salvar(new Usuario("Fulano", papel.name().toLowerCase() + "-" + UUID.randomUUID() + "@exemplo.com",
                "$2a$10$hashfalsoparateste000000000000000000000000000000000000", papel, Instant.now()));
    }

    private String tokenDeAdmin() {
        return servicoDeToken.emitirPara(novoUsuario(Papel.ADMIN)).token();
    }

    private String criarProduto(String token, String preco, int estoque) throws Exception {
        String corpo = mockMvc.perform(post("/api/produtos")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nome":"Produto %s","descricao":"d","preco":%s,"estoqueInicial":%d}
                                """.formatted(UUID.randomUUID(), preco, estoque)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return corpo.replaceAll(".*?\"id\"\\s*:\\s*(\\d+).*", "$1");
    }

    @Test
    @DisplayName("ajuste de estoque usa a quantidade final: repetir a chamada nao soma")
    void ajusteDeEstoqueEIdempotente() throws Exception {
        String token = tokenDeAdmin();
        String id = criarProduto(token, "100.00", 5);

        for (int i = 0; i < 3; i++) {
            mockMvc.perform(put("/api/produtos/" + id + "/estoque")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"estoqueDisponivel\":40}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.estoqueDisponivel").value(40));
        }

        assertThat(produtos.porId(Long.parseLong(id)).orElseThrow().getEstoqueDisponivel())
                .as("tres chamadas, o mesmo estado final — a razao de ser quantidade e nao delta")
                .isEqualTo(40);
    }

    @Test
    @DisplayName("o ajuste mexe no disponivel e preserva o que esta reservado")
    void ajusteNaoTocaNoReservado() throws Exception {
        String token = tokenDeAdmin();
        String id = criarProduto(token, "100.00", 10);

        Usuario cliente = novoUsuario(Papel.CLIENTE);
        pedidoServico.criar(new UsuarioAutenticado(cliente.getId(), cliente.getNome(), cliente.getPapel()),
                UUID.randomUUID().toString(),
                new PedidoRequisicao(List.of(new ItemRequisicao(Long.parseLong(id), 3))));

        mockMvc.perform(put("/api/produtos/" + id + "/estoque")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"estoqueDisponivel\":100}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estoqueDisponivel").value(100))
                .andExpect(jsonPath("$.estoqueReservado").value(3));

        // O reservado pertence a um pedido de cliente: desfaze-lo por aqui seria
        // quebrar uma promessa sem passar pelo cancelamento.
        assertThat(produtos.porId(Long.parseLong(id)).orElseThrow().getEstoqueReservado()).isEqualTo(3);
    }

    @Test
    @DisplayName("desativar tira do catalogo publico sem desfazer pedidos existentes")
    void desativarNaoDesfazPedidos() throws Exception {
        String token = tokenDeAdmin();
        String id = criarProduto(token, "100.00", 10);

        Usuario cliente = novoUsuario(Papel.CLIENTE);
        var resultado = pedidoServico.criar(
                new UsuarioAutenticado(cliente.getId(), cliente.getNome(), cliente.getPapel()),
                UUID.randomUUID().toString(),
                new PedidoRequisicao(List.of(new ItemRequisicao(Long.parseLong(id), 2))));

        mockMvc.perform(put("/api/produtos/" + id + "/situacao")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ativo\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ativo").value(false));

        // Some da vitrine...
        mockMvc.perform(get("/api/produtos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + id + ")]").isEmpty());

        // ...mas continua acessivel por id, e o pedido que ja existe nao muda.
        mockMvc.perform(get("/api/produtos/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ativo").value(false));

        assertThat(produtos.porId(Long.parseLong(id)).orElseThrow().getEstoqueReservado()).isEqualTo(2);
        assertThat(resultado.pedido().getValorTotal()).isEqualByComparingTo("200.00");
    }

    @Test
    @DisplayName("produto inativo nao entra em pedido novo")
    void inativoNaoAceitaPedidoNovo() throws Exception {
        String token = tokenDeAdmin();
        String id = criarProduto(token, "100.00", 10);

        mockMvc.perform(put("/api/produtos/" + id + "/situacao")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ativo\":false}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/pedidos")
                        .header("Authorization", "Bearer " + servicoDeToken.emitirPara(novoUsuario(Papel.CLIENTE)).token())
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"itens":[{"produtoId":%s,"quantidade":1}]}
                                """.formatted(id)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.type").value("https://api.pedidos.dev/erros/produto-inativo"));
    }

    @Test
    @DisplayName("atualizar o preco nao reescreve o historico de quem ja comprou")
    void reajusteNaoMudaPedidoAntigo() throws Exception {
        String token = tokenDeAdmin();
        String id = criarProduto(token, "100.00", 10);

        Usuario cliente = novoUsuario(Papel.CLIENTE);
        var resultado = pedidoServico.criar(
                new UsuarioAutenticado(cliente.getId(), cliente.getNome(), cliente.getPapel()),
                UUID.randomUUID().toString(),
                new PedidoRequisicao(List.of(new ItemRequisicao(Long.parseLong(id), 2))));
        Long pedidoId = resultado.pedido().getId();

        mockMvc.perform(put("/api/produtos/" + id)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nome":"Produto renomeado %s","descricao":"nova","preco":250.00,"estoqueInicial":0}
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.preco").value(250.00));

        mockMvc.perform(get("/api/pedidos/" + pedidoId)
                        .header("Authorization", "Bearer " + servicoDeToken.emitirPara(cliente).token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valorTotal").value(200.00))
                .andExpect(jsonPath("$.itens[0].precoUnitario").value(100.00));

        // O estoque tambem nao muda: estoqueInicial so vale na criacao.
        assertThat(produtos.porId(Long.parseLong(id)).orElseThrow().getEstoqueTotal()).isEqualTo(10);
    }

    @Test
    @DisplayName("nome duplicado e recusado pelo indice unico, ignorando maiusculas")
    void nomeDuplicado() throws Exception {
        String token = tokenDeAdmin();
        String nome = "Produto Unico " + UUID.randomUUID();

        Produto existente = produtos.salvar(new Produto(nome, "d", new BigDecimal("10.00"), 1));
        assertThat(existente.getId()).isNotNull();

        mockMvc.perform(post("/api/produtos")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nome":"%s","descricao":"d","preco":10.00,"estoqueInicial":1}
                                """.formatted(nome.toUpperCase())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("https://api.pedidos.dev/erros/produto-duplicado"));
    }
}
