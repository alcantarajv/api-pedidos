package com.joaoalcantara.pedidos.pedido.dominio;

import com.joaoalcantara.pedidos.comum.erro.RegraDeNegocioException;

/**
 * Tentativa de levar o pedido a um estado que o atual nao permite.
 *
 * <p>422, e nao 409: o conflito aqui nao e com outra transacao, e com o proprio
 * ciclo de vida do pedido. Repetir a requisicao mais tarde nao ajuda — um
 * pedido ENTREGUE nunca voltara a aceitar "pagar".</p>
 */
public class TransicaoInvalidaException extends RegraDeNegocioException {

    public TransicaoInvalidaException(Long pedidoId, StatusPedido atual, StatusPedido destino) {
        super("transicao-invalida",
                "O pedido %d esta %s e nao pode ir para %s".formatted(pedidoId, atual, destino));
    }
}
