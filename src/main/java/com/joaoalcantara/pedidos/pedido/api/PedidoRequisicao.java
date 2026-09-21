package com.joaoalcantara.pedidos.pedido.api;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

/**
 * Criacao de pedido: apenas o que comprar e quanto.
 *
 * <p>O {@code @Valid} vai no argumento de tipo — {@code List<@Valid Item>} — e
 * nao na lista. Aplicado ao container, o Bean Validation avisa que a forma esta
 * obsoleta e a validacao de cada elemento deixa de acontecer.</p>
 */
public record PedidoRequisicao(
        @NotEmpty(message = "informe ao menos um item")
        List<@Valid ItemRequisicao> itens) {
}
