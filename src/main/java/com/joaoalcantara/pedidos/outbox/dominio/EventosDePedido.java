package com.joaoalcantara.pedidos.outbox.dominio;

/** Os tipos de evento que este sistema publica. */
public final class EventosDePedido {

    /** Pagamento confirmado: baixar estoque e avisar o cliente. */
    public static final String PEDIDO_PAGO = "pedido.pago";

    private EventosDePedido() {
    }
}
