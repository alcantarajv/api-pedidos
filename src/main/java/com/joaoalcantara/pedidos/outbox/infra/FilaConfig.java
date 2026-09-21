package com.joaoalcantara.pedidos.outbox.infra;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.joaoalcantara.pedidos.outbox.dominio.EventosDePedido;

/**
 * Topologia da mensageria: exchange, filas e o que acontece com o que falha.
 *
 * <p>Declarada em codigo, e nao no painel do RabbitMQ, pelo mesmo motivo que o
 * schema do banco vive em migrations: infraestrutura criada a mao nao existe na
 * maquina do proximo desenvolvedor, nem no ambiente novo.</p>
 *
 * <p><b>Topic exchange</b> em vez de enviar direto para a fila: o publicador nao
 * precisa saber quem escuta. Quando a Etapa 9 acrescentar o consumidor de
 * notificacao, ele so se liga ao mesmo evento — nenhuma linha do publicador
 * muda.</p>
 *
 * <p>Cada fila tem uma <b>dead letter queue</b>. Sem ela, uma mensagem que o
 * consumidor nao consegue processar volta para a fila para sempre, girando e
 * atrasando todas as outras. Com ela, a mensagem problematica sai do caminho e
 * fica guardada para inspecao — o evento nao se perde e a fila nao trava.</p>
 */
@Configuration
public class FilaConfig {

    public static final String EXCHANGE = "pedidos.eventos";
    public static final String EXCHANGE_MORTAS = "pedidos.eventos.mortas";

    public static final String FILA_ESTOQUE = "pedidos.estoque";
    public static final String FILA_NOTIFICACOES = "pedidos.notificacoes";

    static final String FILA_ESTOQUE_MORTAS = FILA_ESTOQUE + ".mortas";
    static final String FILA_NOTIFICACOES_MORTAS = FILA_NOTIFICACOES + ".mortas";

    @Bean
    TopicExchange exchangeDeEventos() {
        return new TopicExchange(EXCHANGE, true, false);
    }

    @Bean
    TopicExchange exchangeDeMortas() {
        return new TopicExchange(EXCHANGE_MORTAS, true, false);
    }

    @Bean
    Queue filaDeEstoque() {
        return filaDuravelCom(FILA_ESTOQUE, FILA_ESTOQUE_MORTAS);
    }

    @Bean
    Queue filaDeNotificacoes() {
        return filaDuravelCom(FILA_NOTIFICACOES, FILA_NOTIFICACOES_MORTAS);
    }

    @Bean
    Queue filaDeEstoqueMortas() {
        return QueueBuilder.durable(FILA_ESTOQUE_MORTAS).build();
    }

    @Bean
    Queue filaDeNotificacoesMortas() {
        return QueueBuilder.durable(FILA_NOTIFICACOES_MORTAS).build();
    }

    @Bean
    Binding estoqueOuvePedidoPago(Queue filaDeEstoque, TopicExchange exchangeDeEventos) {
        return BindingBuilder.bind(filaDeEstoque).to(exchangeDeEventos).with(EventosDePedido.PEDIDO_PAGO);
    }

    @Bean
    Binding notificacoesOuvemPedidoPago(Queue filaDeNotificacoes, TopicExchange exchangeDeEventos) {
        return BindingBuilder.bind(filaDeNotificacoes).to(exchangeDeEventos).with(EventosDePedido.PEDIDO_PAGO);
    }

    @Bean
    Binding estoqueMortasBinding(Queue filaDeEstoqueMortas, TopicExchange exchangeDeMortas) {
        return BindingBuilder.bind(filaDeEstoqueMortas).to(exchangeDeMortas).with(FILA_ESTOQUE_MORTAS);
    }

    @Bean
    Binding notificacoesMortasBinding(Queue filaDeNotificacoesMortas, TopicExchange exchangeDeMortas) {
        return BindingBuilder.bind(filaDeNotificacoesMortas).to(exchangeDeMortas).with(FILA_NOTIFICACOES_MORTAS);
    }

    /** Fila duravel que encaminha o que falha para a sua fila de mortas. */
    private static Queue filaDuravelCom(String nome, String filaDeMortas) {
        return QueueBuilder.durable(nome)
                .deadLetterExchange(EXCHANGE_MORTAS)
                .deadLetterRoutingKey(filaDeMortas)
                .build();
    }
}
