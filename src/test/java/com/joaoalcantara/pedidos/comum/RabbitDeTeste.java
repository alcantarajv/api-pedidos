package com.joaoalcantara.pedidos.comum;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.rabbitmq.RabbitMQContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * RabbitMQ real para os testes de integracao.
 *
 * <p>Nao ha versao "em memoria" util do RabbitMQ aqui. O comportamento que este
 * projeto precisa exercitar — a publicacao falhar, a mensagem ficar no outbox,
 * o consumidor receber o mesmo evento duas vezes — depende do broker de verdade.
 * Um dublê responderia sempre com sucesso, que e justamente o caso que nao
 * interessa testar.</p>
 *
 * <p>{@code @ServiceConnection} injeta host, porta, usuario e senha do container
 * na configuracao do Spring, sem propriedade escrita a mao.</p>
 */
@TestConfiguration(proxyBeanMethods = false)
public class RabbitDeTeste {

    @Bean
    @ServiceConnection
    RabbitMQContainer rabbit() {
        // Sem o plugin de management: os testes falam com o broker pelo AMQP, e
        // a imagem menor sobe mais rapido no CI.
        return new RabbitMQContainer(DockerImageName.parse("rabbitmq:4-alpine"));
    }
}
