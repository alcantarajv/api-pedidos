package com.joaoalcantara.pedidos.manutencao;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * Por quanto tempo os registros de idempotencia ainda servem.
 *
 * @param retencao precisa ser folgadamente maior que a janela de retentativa do
 *        gateway e do broker. Apagar cedo demais faz um evento antigo ser
 *        reprocessado como se fosse novo — e a protecao inteira vira pó.
 */
@Validated
@ConfigurationProperties(prefix = "pedidos.limpeza")
public record PropriedadesDeLimpeza(
        @NotNull Duration retencao,
        @Positive long intervaloMs) {
}
