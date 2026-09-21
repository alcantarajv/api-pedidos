package com.joaoalcantara.pedidos.comum;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

import com.joaoalcantara.pedidos.outbox.dominio.EventosDePedido;

/**
 * Uma fila so dos testes, ligada aos mesmos eventos que as filas de producao.
 *
 * <p>Existe porque os consumidores de verdade estao ativos no contexto de teste
 * e consomem suas filas imediatamente: um teste que tentasse ler
 * {@code pedidos.estoque} disputaria a mensagem com o consumidor e falharia de
 * forma intermitente.</p>
 *
 * <p>Com o topic exchange, ligar mais uma fila ao mesmo evento nao tira nada de
 * ninguem — cada fila recebe sua copia. Isso tambem demonstra, na pratica, por
 * que o publicador nao deve conhecer os consumidores.</p>
 */
@TestConfiguration(proxyBeanMethods = false)
public class FilaDeTeste {

    public static final String FILA = "pedidos.teste";

    @Bean
    Queue filaDeTeste() {
        // Duravel como as outras, de proposito: uma fila auto-delete faz o
        // RabbitAdmin redeclara-la a cada reconexao, e o ciclo de redeclaracao
        // atrapalha a subida dos listeners. O container do Testcontainers ja
        // morre no fim da suite; nao ha o que limpar.
        return QueueBuilder.durable(FILA).build();
    }

    @Bean
    Binding filaDeTesteOuvePedidoPago(Queue filaDeTeste, TopicExchange exchangeDeEventos) {
        return BindingBuilder.bind(filaDeTeste).to(exchangeDeEventos).with(EventosDePedido.PEDIDO_PAGO);
    }
}
