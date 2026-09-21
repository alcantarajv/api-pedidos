package com.joaoalcantara.pedidos.pedido.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import com.joaoalcantara.pedidos.pedido.dominio.Pedido;
import com.joaoalcantara.pedidos.pedido.dominio.StatusPedido;

public record PedidoResposta(
        Long id,
        Long usuarioId,
        StatusPedido status,
        BigDecimal valorTotal,
        Instant criadoEm,
        List<ItemResposta> itens) {

    public static PedidoResposta de(Pedido pedido) {
        return new PedidoResposta(
                pedido.getId(),
                pedido.getUsuarioId(),
                pedido.getStatus(),
                pedido.getValorTotal(),
                pedido.getCriadoEm(),
                pedido.getItens().stream().map(ItemResposta::de).toList());
    }

    /** Versao sem itens, para listagens — evita carregar a colecao de cada pedido. */
    public static PedidoResposta resumida(Pedido pedido) {
        return new PedidoResposta(
                pedido.getId(),
                pedido.getUsuarioId(),
                pedido.getStatus(),
                pedido.getValorTotal(),
                pedido.getCriadoEm(),
                List.of());
    }
}
