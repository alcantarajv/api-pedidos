package com.joaoalcantara.pedidos.outbox.infra;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * Ajustes do worker de entrega.
 *
 * @param intervaloMs de quanto em quanto tempo o worker procura pendentes. Nao
 *        e latencia garantida: e o atraso maximo entre o fato acontecer e o
 *        evento sair. Um segundo e um meio-termo entre ver o evento chegar
 *        rapido e nao martelar o banco a toa.
 * @param tamanhoDoLote quantos eventos por rodada. Lote grande entrega mais por
 *        ciclo, mas segura conexao do pool por mais tempo.
 * @param timeoutDeConfirmacao quanto esperar o broker confirmar antes de
 *        considerar a publicacao falha. Sem limite, um broker travado
 *        prenderia a thread do agendador para sempre.
 */
@Validated
@ConfigurationProperties(prefix = "pedidos.outbox")
public record PropriedadesDoOutbox(
        @Positive long intervaloMs,
        @Positive int tamanhoDoLote,
        @NotNull Duration timeoutDeConfirmacao) {
}
