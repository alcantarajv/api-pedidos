package com.joaoalcantara.pedidos.outbox.dominio;

/**
 * O broker nao confirmou o recebimento da mensagem.
 *
 * <p>Nao e erro fatal: o evento continua no outbox e sera tentado de novo. E
 * justamente para isso que o padrao existe.</p>
 */
public class FalhaNaPublicacaoException extends RuntimeException {

    public FalhaNaPublicacaoException(String mensagem) {
        super(mensagem);
    }

    public FalhaNaPublicacaoException(String mensagem, Throwable causa) {
        super(mensagem, causa);
    }
}
