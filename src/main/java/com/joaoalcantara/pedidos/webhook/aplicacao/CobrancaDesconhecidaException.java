package com.joaoalcantara.pedidos.webhook.aplicacao;

/**
 * Chegou evento de uma cobranca que este sistema nao conhece.
 *
 * <p>Acontece de verdade: a mesma conta do gateway pode ser usada por outro
 * ambiente (o de testes de um colega, por exemplo), e todos os webhooks vao para
 * a mesma URL configurada. Nao e ataque nem defeito — e evento que nao e nosso.</p>
 */
public class CobrancaDesconhecidaException extends RuntimeException {

    public CobrancaDesconhecidaException(String idExterno) {
        super("Nenhuma cobranca local corresponde a '%s'".formatted(idExterno));
    }
}
