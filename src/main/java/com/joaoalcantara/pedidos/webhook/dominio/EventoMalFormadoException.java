package com.joaoalcantara.pedidos.webhook.dominio;

/**
 * O corpo do webhook nao tem o formato esperado.
 *
 * <p>Traduzida para 400: a assinatura conferiu, entao quem mandou tem o segredo
 * — o problema e o conteudo, e reenviar igual nao vai adiantar.</p>
 */
public class EventoMalFormadoException extends RuntimeException {

    public EventoMalFormadoException(String mensagem) {
        super(mensagem);
    }
}
