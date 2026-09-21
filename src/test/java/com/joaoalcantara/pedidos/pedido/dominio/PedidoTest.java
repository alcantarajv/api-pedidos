package com.joaoalcantara.pedidos.pedido.dominio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.joaoalcantara.pedidos.produto.dominio.Produto;
import com.joaoalcantara.pedidos.usuario.dominio.Papel;
import com.joaoalcantara.pedidos.usuario.dominio.Usuario;

/** Montagem do pedido: fotografia do produto e soma do total. */
class PedidoTest {

    private static final Instant AGORA = Instant.parse("2026-09-21T12:00:00Z");

    private final Usuario cliente = new Usuario("Joao", "joao@exemplo.com", "hash", Papel.CLIENTE, AGORA);

    private Pedido novoPedido() {
        return new Pedido(cliente, "chave-123", AGORA);
    }

    private Produto produto(String nome, String preco, int estoque) {
        return new Produto(nome, "descricao", new BigDecimal(preco), estoque);
    }

    @Test
    @DisplayName("nasce aguardando pagamento, com total zerado")
    void nasceAguardandoPagamento() {
        Pedido pedido = novoPedido();

        assertThat(pedido.getStatus()).isEqualTo(StatusPedido.AGUARDANDO_PAGAMENTO);
        assertThat(pedido.getValorTotal()).isEqualByComparingTo("0.00");
        assertThat(pedido.getItens()).isEmpty();
    }

    @Test
    @DisplayName("o total e a soma dos subtotais")
    void totalSomaOsItens() {
        Pedido pedido = novoPedido();

        pedido.adicionarItem(produto("Teclado", "349.90", 10), 2);
        pedido.adicionarItem(produto("Mouse", "199.95", 10), 1);

        assertThat(pedido.getValorTotal()).isEqualByComparingTo("899.75");
        assertThat(pedido.getItens()).hasSize(2);
    }

    @Test
    @DisplayName("o total tem sempre duas casas decimais")
    void totalComEscalaDefinida() {
        Pedido pedido = novoPedido();

        pedido.adicionarItem(produto("Cabo", "10.00", 5), 3);

        assertThat(pedido.getValorTotal().scale()).isEqualTo(2);
        assertThat(pedido.getValorTotal().toPlainString()).isEqualTo("30.00");
    }

    @Test
    @DisplayName("o item guarda o preco e o nome do momento da compra")
    void itemFotografaOProduto() {
        Produto teclado = produto("Teclado", "349.90", 10);
        Pedido pedido = novoPedido();
        ItemPedido item = pedido.adicionarItem(teclado, 1);

        // O catalogo muda depois da compra.
        teclado.atualizarDados("Teclado Mecanico RGB", "nova descricao", new BigDecimal("499.90"));

        assertThat(item.getPrecoUnitario()).isEqualByComparingTo("349.90");
        assertThat(item.getNomeProduto()).isEqualTo("Teclado");
        assertThat(pedido.getValorTotal()).isEqualByComparingTo("349.90");
    }

    @Test
    @DisplayName("o subtotal e preco vezes quantidade")
    void subtotalDoItem() {
        Pedido pedido = novoPedido();

        ItemPedido item = pedido.adicionarItem(produto("Teclado", "349.90", 10), 3);

        assertThat(item.getSubtotal()).isEqualByComparingTo("1049.70");
    }

    @Test
    @DisplayName("quantidade nao positiva e rejeitada")
    void quantidadeInvalida() {
        Pedido pedido = novoPedido();

        assertThatThrownBy(() -> pedido.adicionarItem(produto("Teclado", "349.90", 10), 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("a lista de itens nao pode ser alterada por fora")
    void itensImutaveisPorFora() {
        Pedido pedido = novoPedido();
        pedido.adicionarItem(produto("Teclado", "349.90", 10), 1);

        assertThatThrownBy(() -> pedido.getItens().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
