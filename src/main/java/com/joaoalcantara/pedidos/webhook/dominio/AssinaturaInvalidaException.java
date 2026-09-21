package com.joaoalcantara.pedidos.webhook.dominio;

/**
 * A requisicao nao veio do gateway — ou veio adulterada no caminho.
 *
 * <p>Endpoint de webhook e publico por natureza: nao ha token de usuario numa
 * chamada feita por outro servidor. A assinatura e o que substitui esse token.
 * Sem ela, qualquer pessoa com a URL confirmaria pedidos de graca mandando um
 * JSON — e este projeto lida com pagamento.</p>
 */
public class AssinaturaInvalidaException extends RuntimeException {

    public AssinaturaInvalidaException(String mensagem) {
        super(mensagem);
    }
}
