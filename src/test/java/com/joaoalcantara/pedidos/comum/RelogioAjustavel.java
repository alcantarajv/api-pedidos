package com.joaoalcantara.pedidos.comum;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Um relogio que o teste move.
 *
 * <p>E aqui que o {@code Clock} injetado desde a Etapa 3 se paga. A regra "o
 * pedido expira em 30 minutos" so e verificavel se der para pular 31 minutos —
 * a alternativa seria configurar um prazo de segundos e esperar de verdade,
 * produzindo um teste lento e instavel que nao prova a regra de producao.</p>
 *
 * <p>{@code @Primary} em vez de sobrescrever o bean {@code relogio}: o contexto
 * ganha os dois e este vence a injecao, sem precisar habilitar sobrescrita de
 * beans.</p>
 */
@TestConfiguration(proxyBeanMethods = false)
public class RelogioAjustavel {

    @Bean
    @Primary
    Clock relogioAjustavel() {
        return new ClockMovel(Instant.now());
    }

    /** Permite avancar o tempo sem trocar a instancia injetada nos beans. */
    public static class ClockMovel extends Clock {

        private final AtomicReference<Instant> agora;

        ClockMovel(Instant inicio) {
            this.agora = new AtomicReference<>(inicio);
        }

        public void avancar(java.time.Duration quanto) {
            agora.updateAndGet(instante -> instante.plus(quanto));
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return agora.get();
        }
    }
}
