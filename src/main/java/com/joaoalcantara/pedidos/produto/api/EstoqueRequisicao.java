package com.joaoalcantara.pedidos.produto.api;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Ajuste administrativo do estoque disponivel.
 *
 * <p>O campo e a quantidade final, nao um delta. Um delta ("+10") parece mais
 * natural, mas nao e idempotente: reenviar a requisicao depois de um timeout
 * somaria de novo. Com a quantidade final, repetir a chamada leva ao mesmo
 * estado — que e o mesmo raciocinio da {@code Idempotency-Key} da Etapa 4,
 * aplicado ao caso mais simples.</p>
 */
public record EstoqueRequisicao(
        @NotNull(message = "e obrigatorio")
        @Min(value = 0, message = "nao pode ser negativo") Integer estoqueDisponivel) {
}
