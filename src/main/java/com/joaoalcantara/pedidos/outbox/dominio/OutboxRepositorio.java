package com.joaoalcantara.pedidos.outbox.dominio;

import java.util.List;
import java.util.Optional;

/** Porta de persistencia do outbox. */
public interface OutboxRepositorio {

    OutboxEvento salvar(OutboxEvento evento);

    /** Candidatos a entrega, sem travar nada: e so a lista de trabalho da rodada. */
    List<OutboxEvento> proximosPendentes(int limite);

    /**
     * Recarrega o evento travando a linha, se ele ainda estiver pendente.
     *
     * <p>{@code FOR UPDATE SKIP LOCKED}: se outra instancia da aplicacao ja
     * estiver entregando este evento, esta chamada devolve vazio em vez de
     * esperar — e o worker segue para o proximo. Sem o {@code SKIP LOCKED},
     * escalar horizontalmente faria os workers formarem fila para a mesma
     * linha, e o throughput de entrega nao subiria com mais instancias.</p>
     *
     * <p>O {@code publicado_em IS NULL} no meio da consulta e o que impede a
     * republicacao de um evento que outro worker acabou de entregar.</p>
     */
    Optional<OutboxEvento> travarSePendente(Long id);

    long contarPendentes();

    /** Remove eventos JA PUBLICADOS anteriores ao limite. Pendentes nunca saem. */
    int apagarPublicadosAnterioresA(java.time.Instant limite);
}
