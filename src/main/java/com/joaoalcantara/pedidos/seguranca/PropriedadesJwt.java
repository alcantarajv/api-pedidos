package com.joaoalcantara.pedidos.seguranca;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Configuracao de assinatura dos tokens.
 *
 * <p>O segredo vem de variavel de ambiente e nunca do repositorio. O
 * {@code @Size(min = 32)} faz a aplicacao falhar no boot se apontarem para um
 * segredo fraco — HS256 exige chave de pelo menos 256 bits, e e melhor nao subir
 * do que subir emitindo token forjavel.</p>
 */
// @Validated e o que faz as constraints abaixo valerem. Sem ele o Spring Boot
// liga as propriedades e ignora @NotBlank e @Size: elas viram decoracao, e a
// aplicacao subiria alegremente com um segredo de 5 caracteres.
@Validated
@ConfigurationProperties(prefix = "pedidos.jwt")
public record PropriedadesJwt(
        @NotBlank(message = "defina a variavel de ambiente JWT_SECRET")
        @Size(min = 32, message = "precisa de no minimo 32 caracteres (256 bits) para HS256")
        String segredo,
        @Positive long expiracaoMinutos,
        @NotBlank String emissor) {
}
