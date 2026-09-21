package com.joaoalcantara.pedidos.consumo.infra;

import java.time.Clock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.joaoalcantara.pedidos.consumo.aplicacao.ConsumoIdempotente;
import com.joaoalcantara.pedidos.consumo.dominio.Notificacao;
import com.joaoalcantara.pedidos.consumo.dominio.NotificacaoRepositorio;
import com.joaoalcantara.pedidos.outbox.dominio.PedidoPago;
import com.joaoalcantara.pedidos.outbox.infra.FilaConfig;

import tools.jackson.databind.ObjectMapper;

/**
 * Avisa o cliente de que o pagamento foi confirmado.
 *
 * <p>Escuta o mesmo evento do consumidor de estoque, numa fila propria. E por
 * isso que a chave de idempotencia inclui o nome do consumidor: as duas filas
 * recebem a mesma mensagem, e cada uma precisa aplicar o seu efeito uma vez.</p>
 *
 * <p>Este e o consumidor onde a idempotencia mais importa. Estoque baixado duas
 * vezes da para corrigir com um ajuste; e-mail enviado duas vezes nao volta.</p>
 */
@Component
public class ConsumidorDeNotificacao {

    private static final Logger log = LoggerFactory.getLogger(ConsumidorDeNotificacao.class);

    private static final String NOME = "notificacao";
    private static final String TIPO = "pagamento-confirmado";

    private final ConsumoIdempotente consumo;
    private final RegistroDeNotificacao registro;
    private final ObjectMapper objectMapper;

    public ConsumidorDeNotificacao(ConsumoIdempotente consumo, RegistroDeNotificacao registro,
                                   ObjectMapper objectMapper) {
        this.consumo = consumo;
        this.registro = registro;
        this.objectMapper = objectMapper;
    }

    @RabbitListener(queues = FilaConfig.FILA_NOTIFICACOES)
    public void receber(@Payload String corpo,
                        @Header(name = "x-id-evento", required = false) String idEvento) {

        if (idEvento == null || idEvento.isBlank()) {
            throw new IllegalArgumentException("Mensagem sem o cabecalho x-id-evento");
        }

        PedidoPago evento = objectMapper.readValue(corpo, PedidoPago.class);

        boolean aplicou = consumo.executar(idEvento, NOME, () -> registro.criar(evento));

        if (aplicou) {
            log.info("Cliente {} notificado sobre o pagamento do pedido {}.",
                    evento.usuarioId(), evento.pedidoId());
        }
    }

    /** O efeito, em bean proprio para que o {@code @Transactional} valha. */
    @Component
    public static class RegistroDeNotificacao {

        private final NotificacaoRepositorio notificacoes;
        private final Clock relogio;

        public RegistroDeNotificacao(NotificacaoRepositorio notificacoes, Clock relogio) {
            this.notificacoes = notificacoes;
            this.relogio = relogio;
        }

        @Transactional(propagation = Propagation.MANDATORY)
        public void criar(PedidoPago evento) {
            notificacoes.salvar(new Notificacao(
                    evento.pedidoId(),
                    evento.usuarioId(),
                    TIPO,
                    "Recebemos o pagamento de R$ %s do seu pedido #%d."
                            .formatted(evento.valorTotal().toPlainString(), evento.pedidoId()),
                    relogio.instant()));
        }
    }
}
