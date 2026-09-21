package com.joaoalcantara.pedidos.consumo.infra;

import java.util.List;

import org.springframework.stereotype.Repository;

import com.joaoalcantara.pedidos.consumo.dominio.Notificacao;
import com.joaoalcantara.pedidos.consumo.dominio.NotificacaoRepositorio;

@Repository
class NotificacaoRepositorioJpa implements NotificacaoRepositorio {

    private final NotificacaoSpringDataRepository jpa;

    NotificacaoRepositorioJpa(NotificacaoSpringDataRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Notificacao salvar(Notificacao notificacao) {
        return jpa.save(notificacao);
    }

    @Override
    public List<Notificacao> doPedido(Long pedidoId) {
        return jpa.findByPedidoId(pedidoId);
    }
}
