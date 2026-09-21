package com.joaoalcantara.pedidos.outbox.dominio;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * O conteudo do evento {@code pedido.pago}.
 *
 * <p>Carrega o necessario para os consumidores agirem sem consultar a API de
 * volta — mas nao o pedido inteiro. Evento gordo vira contrato implicito: o
 * consumidor passa a depender de campos que ninguem prometeu manter.</p>
 */
public record PedidoPago(
        Long pedidoId,
        Long usuarioId,
        BigDecimal valorTotal,
        String idDaCobranca,
        Instant ocorridoEm) {
}
