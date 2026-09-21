package com.joaoalcantara.pedidos.comum.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Liga o agendador do Spring, usado pelo worker do outbox e, na Etapa 10, pela
 * expiracao de pedidos.
 *
 * <p>Fica numa classe propria — e nao anotado na classe da aplicacao — para que
 * um teste possa excluir o agendamento sem mexer no contexto inteiro.</p>
 */
@Configuration
@EnableScheduling
public class AgendamentoConfig {
}
