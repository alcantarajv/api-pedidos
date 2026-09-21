package com.joaoalcantara.pedidos.outbox.infra;

import java.util.concurrent.TimeUnit;

import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import com.joaoalcantara.pedidos.outbox.dominio.FalhaNaPublicacaoException;
import com.joaoalcantara.pedidos.outbox.dominio.OutboxEvento;
import com.joaoalcantara.pedidos.outbox.dominio.PublicadorDeMensagem;

/**
 * Publica no RabbitMQ e so devolve o controle depois da confirmacao do broker.
 *
 * <p>Tres cuidados que separam "mandei" de "chegou":</p>
 *
 * <ol>
 *   <li><b>Confirmacao (publisher confirm).</b> Sem esperar por ela,
 *       {@code convertAndSend} retorna assim que escreve no socket. O broker
 *       pode cair no milissegundo seguinte e a mensagem se perde — e o worker
 *       teria marcado a linha como publicada.</li>
 *   <li><b>Mandatory + retorno.</b> Uma confirmacao positiva significa "o
 *       broker aceitou", nao "alguma fila recebeu". Mensagem publicada em
 *       exchange sem fila ligada e descartada em silencio; com
 *       {@code mandatory}, ela volta, e tratamos como falha.</li>
 *   <li><b>Mensagem persistente.</b> O padrao ja e persistente, mas explicitar
 *       deixa claro que uma reinicializacao do broker nao pode levar junto o
 *       evento de um pedido pago.</li>
 * </ol>
 */
@Component
class PublicadorRabbit implements PublicadorDeMensagem {

    private final RabbitTemplate rabbitTemplate;
    private final PropriedadesDoOutbox propriedades;

    PublicadorRabbit(RabbitTemplate rabbitTemplate, PropriedadesDoOutbox propriedades) {
        this.rabbitTemplate = rabbitTemplate;
        this.propriedades = propriedades;
    }

    @Override
    public void publicar(OutboxEvento evento) {
        // A correlacao carrega o id do evento: quando a confirmacao chega, da
        // para saber de qual mensagem ela fala.
        CorrelationData correlacao = new CorrelationData(evento.getIdEvento());

        rabbitTemplate.convertAndSend(
                FilaConfig.EXCHANGE,
                evento.getTipo(),
                evento.getPayload(),
                mensagem -> {
                    mensagem.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
                    mensagem.getMessageProperties().setContentType("application/json");
                    // Cabecalho lido pelo consumidor como chave de idempotencia.
                    mensagem.getMessageProperties().setHeader(CABECALHO_ID_EVENTO, evento.getIdEvento());
                    mensagem.getMessageProperties().setMessageId(evento.getIdEvento());
                    return mensagem;
                },
                correlacao);

        esperarConfirmacao(correlacao, evento);
    }

    private void esperarConfirmacao(CorrelationData correlacao, OutboxEvento evento) {
        CorrelationData.Confirm confirmacao;
        try {
            confirmacao = correlacao.getFuture()
                    .get(propriedades.timeoutDeConfirmacao().toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new FalhaNaPublicacaoException("Publicacao interrompida", e);
        } catch (Exception e) {
            throw new FalhaNaPublicacaoException(
                    "O broker nao confirmou o evento %s a tempo".formatted(evento.getIdEvento()), e);
        }

        if (confirmacao == null || !confirmacao.ack()) {
            throw new FalhaNaPublicacaoException(
                    "O broker recusou o evento %s: %s".formatted(evento.getIdEvento(),
                            confirmacao == null ? "sem resposta" : confirmacao.reason()));
        }

        // Confirmada, mas devolvida: o exchange existe e nenhuma fila quis a
        // mensagem. Marcar como publicada aqui esconderia um erro de binding.
        if (correlacao.getReturned() != null) {
            throw new FalhaNaPublicacaoException(
                    "O evento %s nao tinha fila de destino (routing key '%s')"
                            .formatted(evento.getIdEvento(), evento.getTipo()));
        }
    }

    static final String CABECALHO_ID_EVENTO = "x-id-evento";
}
