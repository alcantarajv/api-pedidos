package com.joaoalcantara.pedidos.outbox.aplicacao;

import java.time.Clock;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.joaoalcantara.pedidos.outbox.dominio.OutboxEvento;
import com.joaoalcantara.pedidos.outbox.dominio.OutboxRepositorio;
import com.joaoalcantara.pedidos.outbox.dominio.PublicadorDeMensagem;

/**
 * A entrega de um unico evento, em transacao propria.
 *
 * <p>Classe separada de {@link WorkerDoOutbox} por uma razao concreta:
 * {@code @Transactional} so funciona em chamada que passa pelo proxy do Spring.
 * Um metodo chamando outro da mesma classe ignora a anotacao em silencio — a
 * armadilha da auto-invocacao —, e os eventos acabariam todos na mesma
 * transacao sem ninguem perceber.</p>
 */
@Component
public class EntregaDeEvento {

    private static final Logger log = LoggerFactory.getLogger(EntregaDeEvento.class);

    private final OutboxRepositorio outbox;
    private final PublicadorDeMensagem publicador;
    private final Clock relogio;

    public EntregaDeEvento(OutboxRepositorio outbox, PublicadorDeMensagem publicador, Clock relogio) {
        this.outbox = outbox;
        this.publicador = publicador;
        this.relogio = relogio;
    }

    /**
     * @return {@code true} se o broker confirmou o recebimento
     */
    @Transactional
    public boolean entregar(Long id) {
        // Recarrega travando: entre listar e entregar, outra instancia pode ter
        // pegado este evento. Vazio significa "ja esta com alguem" ou "ja foi
        // publicado" — nos dois casos, nao e trabalho nosso.
        Optional<OutboxEvento> travado = outbox.travarSePendente(id);
        if (travado.isEmpty()) {
            return false;
        }

        OutboxEvento evento = travado.get();
        try {
            publicador.publicar(evento);
            // Só depois da confirmacao do broker. Marcar antes transformaria
            // "tentei publicar" em "foi entregue", que e a mentira que o outbox
            // existe para evitar.
            evento.marcarPublicado(relogio.instant());
            outbox.salvar(evento);
            return true;
        } catch (RuntimeException e) {
            // A falha e registrada e a linha continua pendente. Nao relancamos:
            // um evento problematico nao pode impedir a entrega dos outros.
            log.warn("Falha ao publicar o evento {} (tentativa {}): {}",
                    evento.getIdEvento(), evento.getTentativas() + 1, e.getMessage());
            evento.registrarFalha(e.getMessage());
            outbox.salvar(evento);
            return false;
        }
    }
}
