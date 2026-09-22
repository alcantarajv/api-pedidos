package com.joaoalcantara.pedidos.pedido.infra;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Repository;

import com.joaoalcantara.pedidos.pedido.dominio.Pedido;
import com.joaoalcantara.pedidos.pedido.dominio.PedidoRepositorio;

/** Adaptador que liga a porta do dominio ao Spring Data. */
@Repository
class PedidoRepositorioJpa implements PedidoRepositorio {

    private final PedidoSpringDataRepository jpa;

    PedidoRepositorioJpa(PedidoSpringDataRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Pedido salvar(Pedido pedido) {
        return jpa.save(pedido);
    }

    @Override
    public Optional<Pedido> porId(Long id) {
        return jpa.findById(id);
    }

    @Override
    public Optional<Pedido> porIdComItens(Long id) {
        return jpa.buscarComItens(id);
    }

    @Override
    public Optional<Pedido> porIdComTrava(Long id) {
        return jpa.buscarComTrava(id);
    }

    @Override
    public Optional<Pedido> porChaveDeIdempotencia(Long usuarioId, String chave) {
        return jpa.findByUsuario_IdAndChaveIdempotencia(usuarioId, chave);
    }

    @Override
    public List<Pedido> listarDoUsuario(Long usuarioId) {
        return jpa.findByUsuario_IdOrderByCriadoEmDesc(usuarioId);
    }

    @Override
    public List<Pedido> listarTodos() {
        return jpa.findAllByOrderByCriadoEmDesc();
    }

    @Override
    public List<Long> idsAguardandoPagamentoDesdeAntesDe(java.time.Instant limite, int quantidade) {
        return jpa.idsExpirados(limite, org.springframework.data.domain.PageRequest.of(0, quantidade));
    }
}
