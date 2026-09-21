package com.joaoalcantara.pedidos.produto.dominio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.joaoalcantara.pedidos.comum.erro.RegraDeNegocioException;

/**
 * Regras de estoque do produto.
 *
 * <p>Testes unitarios puros: sem banco, sem Spring, sem transacao. A regra de
 * estoque vive na entidade justamente para poder ser exercitada assim — o ciclo
 * inteiro (reservar, confirmar, devolver) e verificado em milissegundos.</p>
 */
class ProdutoTest {

    private Produto produto;

    @BeforeEach
    void criaProdutoComDezUnidades() {
        produto = new Produto("Teclado Mecanico", "ABNT2, switch marrom", new BigDecimal("349.90"), 10);
    }

    @Test
    @DisplayName("nasce com todo o estoque disponivel e nada reservado")
    void nasceComEstoqueTodoDisponivel() {
        assertThat(produto.getEstoqueDisponivel()).isEqualTo(10);
        assertThat(produto.getEstoqueReservado()).isZero();
        assertThat(produto.getEstoqueTotal()).isEqualTo(10);
        assertThat(produto.isAtivo()).isTrue();
    }

    @Test
    @DisplayName("reservar move unidades do disponivel para o reservado, sem mudar o total")
    void reservarMoveEntreAsParcelas() {
        produto.reservar(3);

        assertThat(produto.getEstoqueDisponivel()).isEqualTo(7);
        assertThat(produto.getEstoqueReservado()).isEqualTo(3);
        assertThat(produto.getEstoqueTotal()).isEqualTo(10);
    }

    @Test
    @DisplayName("reservar mais do que o disponivel e recusado")
    void reservarAlemDoDisponivel() {
        assertThatThrownBy(() -> produto.reservar(11))
                .isInstanceOf(EstoqueInsuficienteException.class)
                .hasMessageContaining("11")
                .hasMessageContaining("10");

        assertThat(produto.getEstoqueDisponivel()).isEqualTo(10);
        assertThat(produto.getEstoqueReservado()).isZero();
    }

    @Test
    @DisplayName("o reservado nao conta como disponivel para uma reserva nova")
    void reservadoNaoEstaDisponivel() {
        produto.reservar(10);

        assertThatThrownBy(() -> produto.reservar(1))
                .isInstanceOf(EstoqueInsuficienteException.class);
    }

    @Test
    @DisplayName("confirmar a venda tira do reservado e nao devolve ao disponivel")
    void confirmarVendaBaixaDefinitiva() {
        produto.reservar(4);

        produto.confirmarVenda(4);

        assertThat(produto.getEstoqueReservado()).isZero();
        assertThat(produto.getEstoqueDisponivel()).isEqualTo(6);
        assertThat(produto.getEstoqueTotal()).isEqualTo(6);
    }

    @Test
    @DisplayName("devolver a reserva recompoe o disponivel")
    void devolverReservaRecompoe() {
        produto.reservar(4);

        produto.devolverReserva(4);

        assertThat(produto.getEstoqueDisponivel()).isEqualTo(10);
        assertThat(produto.getEstoqueReservado()).isZero();
    }

    @Test
    @DisplayName("baixar mais do que foi reservado e defeito de programacao, nao regra de negocio")
    void confirmarMaisDoQueFoiReservado() {
        produto.reservar(2);

        assertThatThrownBy(() -> produto.confirmarVenda(3))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("quantidade zero ou negativa e rejeitada em qualquer operacao de estoque")
    void quantidadeNaoPositiva() {
        assertThatThrownBy(() -> produto.reservar(0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> produto.reservar(-1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> produto.confirmarVenda(0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> produto.devolverReserva(-2)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("ajuste administrativo mexe no disponivel e preserva o reservado")
    void ajusteNaoTocaNoReservado() {
        produto.reservar(3);

        produto.ajustarEstoqueDisponivel(50);

        assertThat(produto.getEstoqueDisponivel()).isEqualTo(50);
        assertThat(produto.getEstoqueReservado()).isEqualTo(3);
    }

    @Test
    @DisplayName("ajuste para valor negativo e rejeitado")
    void ajusteNegativo() {
        assertThatThrownBy(() -> produto.ajustarEstoqueDisponivel(-1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("produto inativo nao entra em pedido novo, mesmo com estoque")
    void inativoNaoVende() {
        produto.desativar();

        assertThatThrownBy(() -> produto.exigirDisponivelParaVenda())
                .isInstanceOf(RegraDeNegocioException.class)
                .hasMessageContaining("Teclado Mecanico");

        assertThat(produto.getEstoqueDisponivel()).isEqualTo(10);
    }

    @Test
    @DisplayName("desativar nao desfaz reservas que ja existem")
    void desativarPreservaReservas() {
        produto.reservar(3);

        produto.desativar();

        assertThat(produto.getEstoqueReservado()).isEqualTo(3);
    }
}
