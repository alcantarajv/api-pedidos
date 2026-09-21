package com.joaoalcantara.pedidos.produto.infra;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import com.joaoalcantara.pedidos.produto.dominio.Produto;

import jakarta.persistence.LockModeType;

/** Repositorio Spring Data. Detalhe de infraestrutura: ninguem fora deste pacote o usa. */
interface ProdutoSpringDataRepository extends JpaRepository<Produto, Long> {

    List<Produto> findByAtivoTrueOrderByNomeAsc();

    List<Produto> findAllByOrderByNomeAsc();

    boolean existsByNomeIgnoreCase(String nome);

    /**
     * PESSIMISTIC_WRITE vira {@code SELECT ... FOR UPDATE} no PostgreSQL: a linha
     * fica travada ate o fim da transacao, e outra transacao que peca a mesma
     * linha espera.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Produto p where p.id = :id")
    Optional<Produto> buscarComTrava(Long id);
}
