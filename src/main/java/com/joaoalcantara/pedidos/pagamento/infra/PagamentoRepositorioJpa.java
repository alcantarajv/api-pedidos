package com.joaoalcantara.pedidos.pagamento.infra;

import java.util.Optional;

import org.springframework.stereotype.Repository;

import com.joaoalcantara.pedidos.pagamento.dominio.Pagamento;
import com.joaoalcantara.pedidos.pagamento.dominio.PagamentoRepositorio;

@Repository
class PagamentoRepositorioJpa implements PagamentoRepositorio {

    private final PagamentoSpringDataRepository jpa;

    PagamentoRepositorioJpa(PagamentoSpringDataRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Pagamento salvar(Pagamento pagamento) {
        return jpa.save(pagamento);
    }

    @Override
    public Optional<Pagamento> porPedido(Long pedidoId) {
        return jpa.findByPedido_Id(pedidoId);
    }

    @Override
    public Optional<Pagamento> porIdExterno(String idExterno) {
        return jpa.findByIdExterno(idExterno);
    }
}
