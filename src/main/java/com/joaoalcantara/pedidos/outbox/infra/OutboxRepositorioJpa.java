package com.joaoalcantara.pedidos.outbox.infra;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import com.joaoalcantara.pedidos.outbox.dominio.OutboxEvento;
import com.joaoalcantara.pedidos.outbox.dominio.OutboxRepositorio;

@Repository
class OutboxRepositorioJpa implements OutboxRepositorio {

    private final OutboxSpringDataRepository jpa;

    OutboxRepositorioJpa(OutboxSpringDataRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public OutboxEvento salvar(OutboxEvento evento) {
        return jpa.save(evento);
    }

    @Override
    public List<OutboxEvento> proximosPendentes(int limite) {
        return jpa.findByPublicadoEmIsNullOrderByCriadoEmAsc(PageRequest.of(0, limite));
    }

    @Override
    public Optional<OutboxEvento> travarSePendente(Long id) {
        return jpa.travarSePendente(id);
    }

    @Override
    public long contarPendentes() {
        return jpa.countByPublicadoEmIsNull();
    }

    @Override
    public int apagarPublicadosAnterioresA(java.time.Instant limite) {
        return jpa.deleteByPublicadoEmIsNotNullAndPublicadoEmBefore(limite);
    }
}
