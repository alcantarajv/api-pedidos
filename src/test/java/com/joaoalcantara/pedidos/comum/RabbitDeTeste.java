package com.joaoalcantara.pedidos.comum;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.rabbitmq.RabbitMQContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * RabbitMQ real para os testes de integracao, um so para a suite inteira.
 *
 * <p>Nao ha versao "em memoria" util do RabbitMQ aqui. O comportamento que este
 * projeto precisa exercitar — a publicacao falhar, a mensagem ficar no outbox, o
 * consumidor receber o mesmo evento duas vezes, a mensagem ruim cair na fila de
 * mortas — depende do broker de verdade. Um dublê responderia sempre com
 * sucesso, que e justamente o caso que nao interessa testar.</p>
 *
 * <p>Estatico pelo mesmo motivo do PostgreSQL: sete contextos de teste
 * levantariam sete brokers. Ver {@link PostgresDeTeste} para o detalhe do
 * {@code destroyMethod = ""}.</p>
 *
 * <p>Consequencia de compartilhar: o estado do broker atravessa classes de
 * teste. Filas nao sao recriadas entre elas, e mensagem que sobrou de um teste
 * chega no proximo. Os testes daqui lidam com isso esvaziando as filas que usam
 * e referenciando dados que existem — nao e detalhe de estilo, e o que mantem a
 * suite estavel.</p>
 */
@TestConfiguration(proxyBeanMethods = false)
public class RabbitDeTeste {

    // Sem o plugin de management: os testes falam com o broker pelo AMQP, e a
    // imagem menor sobe mais rapido no CI.
    private static final RabbitMQContainer CONTAINER =
            new RabbitMQContainer(DockerImageName.parse("rabbitmq:4-alpine"));

    static {
        CONTAINER.start();
    }

    /**
     * {@code @ServiceConnection} injeta host, porta, usuario e senha do container
     * na configuracao do Spring, sem propriedade escrita a mao.
     */
    @Bean(destroyMethod = "")
    @ServiceConnection
    RabbitMQContainer rabbit() {
        return CONTAINER;
    }
}
