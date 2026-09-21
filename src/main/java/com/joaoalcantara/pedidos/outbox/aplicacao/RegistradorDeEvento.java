package com.joaoalcantara.pedidos.outbox.aplicacao;

import java.time.Clock;

import org.springframework.stereotype.Component;

import com.joaoalcantara.pedidos.outbox.dominio.OutboxEvento;
import com.joaoalcantara.pedidos.outbox.dominio.OutboxRepositorio;

import tools.jackson.databind.ObjectMapper;

/**
 * Grava um evento de dominio no outbox.
 *
 * <p>Nao abre transacao propria: e chamado de dentro da transacao que produziu o
 * fato, e e exatamente isso que da a garantia do padrao. Um
 * {@code REQUIRES_NEW} aqui quebraria tudo em silencio — o evento seria
 * commitado sozinho e sobreviveria ao rollback do fato que o originou.</p>
 */
@Component
public class RegistradorDeEvento {

    private final OutboxRepositorio outbox;
    private final ObjectMapper objectMapper;
    private final Clock relogio;

    public RegistradorDeEvento(OutboxRepositorio outbox, ObjectMapper objectMapper, Clock relogio) {
        this.outbox = outbox;
        this.objectMapper = objectMapper;
        this.relogio = relogio;
    }

    public OutboxEvento registrar(String tipo, String agregadoId, Object payload) {
        String json = objectMapper.writeValueAsString(payload);
        return outbox.salvar(new OutboxEvento(tipo, agregadoId, json, relogio.instant()));
    }
}
