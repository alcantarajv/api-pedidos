package com.joaoalcantara.pedidos.outbox.aplicacao;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.joaoalcantara.pedidos.outbox.dominio.OutboxEvento;
import com.joaoalcantara.pedidos.outbox.dominio.OutboxRepositorio;
import com.joaoalcantara.pedidos.outbox.infra.PropriedadesDoOutbox;

/**
 * O processo separado que entrega o que o outbox acumulou.
 *
 * <p>Roda em intervalo fixo: lista os pendentes e entrega um por um.</p>
 *
 * <p><b>Este metodo nao e transacional, de proposito.</b> Cada evento e
 * entregue em transacao propria ({@link EntregaDeEvento}). Um lote inteiro numa
 * transacao so teria dois problemas: uma falha no ultimo evento desfaria a
 * marcacao dos anteriores — que ja haviam sido entregues de verdade, e seriam
 * publicados de novo —, e a transacao ficaria aberta durante todas as chamadas
 * de rede ao broker, segurando conexao do pool por muito mais tempo do que
 * precisa.</p>
 *
 * <p>Uma falha tambem nao interrompe o lote: um evento problematico — uma
 * routing key sem fila ligada, por exemplo — travaria a entrega de todos os
 * outros. Ele fica pendente, com a falha registrada, e a fila anda.</p>
 */
@Component
public class WorkerDoOutbox {

    private static final Logger log = LoggerFactory.getLogger(WorkerDoOutbox.class);

    private final OutboxRepositorio outbox;
    private final EntregaDeEvento entrega;
    private final PropriedadesDoOutbox propriedades;

    public WorkerDoOutbox(OutboxRepositorio outbox, EntregaDeEvento entrega, PropriedadesDoOutbox propriedades) {
        this.outbox = outbox;
        this.entrega = entrega;
        this.propriedades = propriedades;
    }

    @Scheduled(fixedDelayString = "${pedidos.outbox.intervalo-ms:1000}")
    public void rodar() {
        despacharLote();
    }

    /**
     * Processa um lote e devolve quantos foram entregues.
     *
     * <p>Publico para que os testes chamem direto, em vez de esperar o
     * agendador: teste que depende de {@code sleep} e lento e instavel.</p>
     */
    public int despacharLote() {
        List<OutboxEvento> pendentes = outbox.proximosPendentes(propriedades.tamanhoDoLote());
        if (pendentes.isEmpty()) {
            return 0;
        }

        int entregues = 0;
        for (OutboxEvento pendente : pendentes) {
            if (entrega.entregar(pendente.getId())) {
                entregues++;
            }
        }

        if (entregues < pendentes.size()) {
            log.warn("Lote do outbox: {} de {} entregues; o restante fica para a proxima rodada.",
                    entregues, pendentes.size());
        }
        return entregues;
    }
}
