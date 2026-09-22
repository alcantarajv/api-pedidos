package com.joaoalcantara.pedidos.webhook.infra;

import org.springframework.stereotype.Repository;

import com.joaoalcantara.pedidos.webhook.dominio.EventoProcessado;
import com.joaoalcantara.pedidos.webhook.dominio.EventoProcessadoRepositorio;

@Repository
class EventoProcessadoRepositorioJpa implements EventoProcessadoRepositorio {

    private final EventoProcessadoSpringDataRepository jpa;

    EventoProcessadoRepositorioJpa(EventoProcessadoSpringDataRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public EventoProcessado salvar(EventoProcessado evento) {
        return jpa.save(evento);
    }

    @Override
    public boolean jaProcessado(String idExterno) {
        return jpa.existsByIdExterno(idExterno);
    }

    @Override
    public int apagarAnterioresA(java.time.Instant limite) {
        return jpa.deleteByProcessadoEmBefore(limite);
    }
}
