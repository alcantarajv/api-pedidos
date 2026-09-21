package com.joaoalcantara.pedidos.produto.api;

import jakarta.validation.constraints.NotNull;

/** Ativacao ou desativacao do produto no catalogo. */
public record SituacaoRequisicao(@NotNull(message = "e obrigatorio") Boolean ativo) {
}
