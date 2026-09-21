package com.joaoalcantara.pedidos.pedido.api;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Uma linha do pedido pedida pelo cliente.
 *
 * <p>Repare no que NAO vem aqui: o preco. Quem informa quanto custa e o
 * catalogo, no servidor. Aceitar preco do cliente permitiria comprar por
 * qualquer valor.</p>
 */
public record ItemRequisicao(
        @NotNull(message = "e obrigatorio") Long produtoId,
        @NotNull(message = "e obrigatoria")
        @Min(value = 1, message = "deve ser pelo menos 1") Integer quantidade) {
}
