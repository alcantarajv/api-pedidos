package com.joaoalcantara.pedidos.outbox.infra;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.joaoalcantara.pedidos.outbox.dominio.OutboxEvento;

interface OutboxSpringDataRepository extends JpaRepository<OutboxEvento, Long> {

    /**
     * A ordem por criado_em entrega os eventos aproximadamente na ordem em que
     * os fatos aconteceram. "Aproximadamente" e honesto: com varios workers em
     * paralelo nao ha ordem total, e o consumidor precisa tolerar isso.
     */
    List<OutboxEvento> findByPublicadoEmIsNullOrderByCriadoEmAsc(org.springframework.data.domain.Pageable limite);

    /**
     * Consulta nativa de proposito: {@code FOR UPDATE SKIP LOCKED} e do
     * PostgreSQL e nao tem equivalente em JPQL.
     */
    @Query(value = """
            SELECT * FROM outbox
            WHERE id = :id AND publicado_em IS NULL
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    Optional<OutboxEvento> travarSePendente(@Param("id") Long id);

    long countByPublicadoEmIsNull();
}
