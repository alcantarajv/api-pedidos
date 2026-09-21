package com.joaoalcantara.pedidos.consumo.infra;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.joaoalcantara.pedidos.consumo.dominio.Notificacao;

interface NotificacaoSpringDataRepository extends JpaRepository<Notificacao, Long> {

    List<Notificacao> findByPedidoId(Long pedidoId);
}
