package com.joaoalcantara.pedidos.webhook.dominio;

/** Porta de persistencia do registro de eventos consumidos. */
public interface EventoProcessadoRepositorio {

    EventoProcessado salvar(EventoProcessado evento);

    boolean jaProcessado(String idExterno);
}
