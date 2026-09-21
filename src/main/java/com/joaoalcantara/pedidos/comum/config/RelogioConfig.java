package com.joaoalcantara.pedidos.comum.config;

import java.time.Clock;
import java.time.ZoneOffset;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Expoe o relogio da aplicacao como bean.
 *
 * <p>Nenhuma regra de negocio deve chamar {@code Instant.now()} diretamente. Toda
 * decisao que depende de tempo — expiracao de token agora, expiracao de pedido
 * nao pago na Etapa 10, idade de um evento no outbox na Etapa 8 — recebe este
 * {@link Clock} injetado. Nos testes ele vira {@code Clock.fixed(...)}, e a
 * regra passa a ser verificavel em milissegundos em vez de esperar o relogio.</p>
 */
@Configuration
public class RelogioConfig {

    @Bean
    public Clock relogio() {
        return Clock.system(ZoneOffset.UTC);
    }
}
