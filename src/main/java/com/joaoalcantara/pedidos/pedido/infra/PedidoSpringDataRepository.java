package com.joaoalcantara.pedidos.pedido.infra;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.joaoalcantara.pedidos.pedido.dominio.Pedido;

interface PedidoSpringDataRepository extends JpaRepository<Pedido, Long> {

    @Query("select distinct p from Pedido p left join fetch p.itens where p.id = :id")
    Optional<Pedido> buscarComItens(Long id);

    // O underline forca a travessia pela associacao: "usuario.id", e nao um campo
    // chamado "usuarioId" — que nao existe na entidade.
    Optional<Pedido> findByUsuario_IdAndChaveIdempotencia(Long usuarioId, String chaveIdempotencia);

    List<Pedido> findByUsuario_IdOrderByCriadoEmDesc(Long usuarioId);

    List<Pedido> findAllByOrderByCriadoEmDesc();
}
