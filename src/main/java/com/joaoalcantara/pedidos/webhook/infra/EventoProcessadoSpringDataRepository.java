package com.joaoalcantara.pedidos.webhook.infra;

import org.springframework.data.jpa.repository.JpaRepository;

import com.joaoalcantara.pedidos.webhook.dominio.EventoProcessado;

interface EventoProcessadoSpringDataRepository extends JpaRepository<EventoProcessado, Long> {

    boolean existsByIdExterno(String idExterno);

    int deleteByProcessadoEmBefore(java.time.Instant limite);
}
