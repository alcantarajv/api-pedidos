package com.joaoalcantara.pedidos.produto.aplicacao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.joaoalcantara.pedidos.comum.erro.ConflitoException;
import com.joaoalcantara.pedidos.comum.erro.RecursoNaoEncontradoException;
import com.joaoalcantara.pedidos.produto.api.ProdutoRequisicao;
import com.joaoalcantara.pedidos.produto.dominio.Produto;
import com.joaoalcantara.pedidos.produto.dominio.ProdutoRepositorio;

/** Casos de uso do catalogo, com a porta de persistencia substituida por um duble. */
class ProdutoServicoTest {

    private final ProdutoRepositorio repositorio = mock(ProdutoRepositorio.class);
    private final ProdutoServico servico = new ProdutoServico(repositorio);

    @BeforeEach
    void devolveOQueRecebe() {
        when(repositorio.salvar(any(Produto.class))).thenAnswer(chamada -> chamada.getArgument(0));
        when(repositorio.existeComNome(anyString())).thenReturn(false);
    }

    private ProdutoRequisicao requisicao(String nome, String preco, int estoque) {
        return new ProdutoRequisicao(nome, "descricao", new BigDecimal(preco), estoque);
    }

    @Test
    @DisplayName("cria o produto com o estoque inicial informado")
    void criaComEstoqueInicial() {
        Produto produto = servico.criar(requisicao("Teclado", "349.90", 10));

        assertThat(produto.getNome()).isEqualTo("Teclado");
        assertThat(produto.getEstoqueDisponivel()).isEqualTo(10);
        assertThat(produto.getEstoqueReservado()).isZero();
    }

    @Test
    @DisplayName("recusa nome ja usado por outro produto")
    void recusaNomeDuplicado() {
        when(repositorio.existeComNome("Teclado")).thenReturn(true);

        assertThatThrownBy(() -> servico.criar(requisicao("Teclado", "349.90", 10)))
                .isInstanceOf(ConflitoException.class)
                .hasMessageContaining("Teclado");
    }

    @Test
    @DisplayName("atualizar mantendo o proprio nome nao dispara conflito")
    void atualizarComOMesmoNome() {
        Produto existente = new Produto("Teclado", "antiga", new BigDecimal("349.90"), 10);
        when(repositorio.porId(1L)).thenReturn(Optional.of(existente));
        when(repositorio.existeComNome("Teclado")).thenReturn(true);

        Produto atualizado = servico.atualizar(1L, requisicao("Teclado", "299.90", 999));

        assertThat(atualizado.getPreco()).isEqualByComparingTo("299.90");
    }

    @Test
    @DisplayName("atualizar nao altera o estoque, mesmo com estoqueInicial no corpo")
    void atualizarIgnoraEstoque() {
        Produto existente = new Produto("Teclado", "antiga", new BigDecimal("349.90"), 10);
        existente.reservar(2);
        when(repositorio.porId(1L)).thenReturn(Optional.of(existente));

        Produto atualizado = servico.atualizar(1L, requisicao("Teclado", "299.90", 999));

        assertThat(atualizado.getEstoqueDisponivel()).isEqualTo(8);
        assertThat(atualizado.getEstoqueReservado()).isEqualTo(2);
    }

    @Test
    @DisplayName("ajuste de estoque troca o disponivel pela quantidade final enviada")
    void ajusteUsaQuantidadeFinal() {
        Produto existente = new Produto("Teclado", "d", new BigDecimal("349.90"), 10);
        when(repositorio.porId(1L)).thenReturn(Optional.of(existente));

        servico.ajustarEstoque(1L, 4);
        servico.ajustarEstoque(1L, 4);

        assertThat(existente.getEstoqueDisponivel()).isEqualTo(4);
    }

    @Test
    @DisplayName("produto inexistente vira 404, nao erro generico")
    void produtoInexistente() {
        when(repositorio.porId(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servico.buscar(99L))
                .isInstanceOf(RecursoNaoEncontradoException.class)
                .hasMessageContaining("99");
    }
}
