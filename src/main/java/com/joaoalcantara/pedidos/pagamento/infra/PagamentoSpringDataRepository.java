package com.joaoalcantara.pedidos.pagamento.infra;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.joaoalcantara.pedidos.pagamento.dominio.Pagamento;

interface PagamentoSpringDataRepository extends JpaRepository<Pagamento, Long> {

    Optional<Pagamento> findByPedido_Id(Long pedidoId);

    Optional<Pagamento> findByIdExterno(String idExterno);
}
