package com.joaoalcantara.pedidos.outbox.dominio;

/**
 * Porta de saida para o broker.
 *
 * <p>Existe para que o worker do outbox possa ser testado contra um publicador
 * que falha de proposito — o caminho que mais importa e o que e mais dificil de
 * provocar com o broker de verdade.</p>
 */
public interface PublicadorDeMensagem {

    /**
     * Publica e <b>espera a confirmacao</b> do broker.
     *
     * @throws FalhaNaPublicacaoException se o broker nao confirmar, recusar ou
     *         devolver a mensagem por nao haver fila para ela
     */
    void publicar(OutboxEvento evento);
}
