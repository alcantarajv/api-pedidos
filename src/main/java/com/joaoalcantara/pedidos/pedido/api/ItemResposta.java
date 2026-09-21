package com.joaoalcantara.pedidos.pedido.api;

import java.math.BigDecimal;

import com.joaoalcantara.pedidos.pedido.dominio.ItemPedido;

/**
 * Linha do pedido como ela foi comprada.
 *
 * <p>O {@code nomeProduto} e o {@code precoUnitario} vem do item, nao do produto
 * atual: e a fotografia do momento da compra.</p>
 */
public record ItemResposta(
        Long produtoId,
        String nomeProduto,
        int quantidade,
        BigDecimal precoUnitario,
        BigDecimal subtotal) {

    public static ItemResposta de(ItemPedido item) {
        return new ItemResposta(
                item.getProdutoId(),
                item.getNomeProduto(),
                item.getQuantidade(),
                item.getPrecoUnitario(),
                item.getSubtotal());
    }
}
