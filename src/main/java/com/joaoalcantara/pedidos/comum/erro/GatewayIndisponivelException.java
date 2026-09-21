package com.joaoalcantara.pedidos.comum.erro;

/**
 * O gateway de pagamento nao respondeu, ou respondeu com erro.
 *
 * <p>Traduzida para 502 Bad Gateway — e nao 500. A distincao importa para quem
 * consome a API: 500 diz "o defeito e nosso, nao adianta tentar de novo"; 502
 * diz "quem falhou foi um sistema de que dependemos, tente mais tarde". Com a
 * chave de idempotencia em maos, tentar de novo e seguro.</p>
 */
public class GatewayIndisponivelException extends RuntimeException {

    public GatewayIndisponivelException(String mensagem) {
        super(mensagem);
    }
}
