package com.joaoalcantara.pedidos.produto.infra;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.joaoalcantara.pedidos.produto.dominio.Produto;

/** Repositorio Spring Data. Detalhe de infraestrutura: ninguem fora deste pacote o usa. */
interface ProdutoSpringDataRepository extends JpaRepository<Produto, Long> {

    List<Produto> findByAtivoTrueOrderByNomeAsc();

    List<Produto> findAllByOrderByNomeAsc();

    boolean existsByNomeIgnoreCase(String nome);
}
