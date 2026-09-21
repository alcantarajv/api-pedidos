package com.joaoalcantara.pedidos.usuario.api;

import jakarta.validation.constraints.NotBlank;

public record LoginRequisicao(
        @NotBlank(message = "e obrigatorio") String email,
        @NotBlank(message = "e obrigatoria") String senha) {
}
