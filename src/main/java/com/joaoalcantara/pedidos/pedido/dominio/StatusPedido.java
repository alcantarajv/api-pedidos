package com.joaoalcantara.pedidos.pedido.dominio;

/**
 * Situacao do pedido no seu ciclo de vida.
 *
 * <pre>
 * AGUARDANDO_PAGAMENTO --&gt; PAGO --&gt; SEPARANDO --&gt; ENVIADO --&gt; ENTREGUE
 *          |               |
 *          --&gt; CANCELADO   --&gt; REEMBOLSADO
 * </pre>
 *
 * <p>As transicoes permitidas entre estes estados sao tema da Etapa 5. Por ora o
 * enum existe inteiro porque a coluna precisa de um dominio fechado desde a
 * primeira migration — acrescentar valor a um CHECK depois e outra migration, e
 * o desenho do ciclo ja esta decidido.</p>
 */
public enum StatusPedido {
    AGUARDANDO_PAGAMENTO,
    PAGO,
    SEPARANDO,
    ENVIADO,
    ENTREGUE,
    CANCELADO,
    REEMBOLSADO
}
