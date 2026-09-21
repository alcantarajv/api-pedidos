package com.joaoalcantara.pedidos.usuario.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Entrada de registro publico.
 *
 * <p>Repare no que NAO esta aqui: o papel. Ele nao e aceito na requisicao — quem
 * se registra e sempre CLIENTE. Um campo {@code papel} no corpo permitiria que
 * qualquer um se autopromovesse a administrador do catalogo.</p>
 *
 * <p>O limite de 72 caracteres na senha nao e arbitrario: BCrypt trunca a
 * entrada nesse ponto. Aceitar mais daria a falsa impressao de que uma senha de
 * 100 caracteres esta sendo inteiramente usada.</p>
 */
public record RegistroRequisicao(
        @NotBlank(message = "e obrigatorio")
        @Size(max = 120, message = "deve ter no maximo 120 caracteres") String nome,

        @NotBlank(message = "e obrigatorio") @Email(message = "formato invalido")
        @Size(max = 180, message = "deve ter no maximo 180 caracteres") String email,

        @NotBlank(message = "e obrigatoria")
        @Size(min = 8, max = 72, message = "deve ter entre 8 e 72 caracteres") String senha) {
}
