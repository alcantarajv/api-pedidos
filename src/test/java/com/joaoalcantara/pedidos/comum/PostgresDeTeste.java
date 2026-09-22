package com.joaoalcantara.pedidos.comum;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * PostgreSQL real para os testes de integracao, <b>um so para a suite inteira</b>.
 *
 * <p>Banco em memoria nao serviria: a idempotencia deste projeto depende de uma
 * violacao de restricao de unicidade acontecer exatamente como acontece em
 * producao, o {@code FOR UPDATE SKIP LOCKED} do outbox e do PostgreSQL, e os
 * indices parciais tambem. Testar contra outro banco validaria um sistema
 * diferente do que roda de verdade.</p>
 *
 * <h2>Por que o container e estatico</h2>
 *
 * <p>Esta suite tem sete configuracoes de contexto diferentes — umas com MockMvc,
 * outras com relogio ajustavel, outra com o publicador substituido. O Spring
 * cacheia cada uma separadamente, e com um container declarado por contexto isso
 * virava <b>catorze containers</b> subindo numa execucao: sete PostgreSQL e sete
 * RabbitMQ.</p>
 *
 * <p>Com a instancia estatica, o container sobe uma vez por JVM e e reaproveitado
 * por todos os contextos. O {@code destroyMethod = ""} e a parte que falta em
 * quase todo exemplo: sem ele, o Spring chama {@code stop()} ao fechar o
 * primeiro contexto e os seguintes encontram um container morto.</p>
 *
 * <p>Quem limpa no fim e o Ryuk, o container auxiliar do proprio Testcontainers,
 * que derruba tudo quando a JVM termina.</p>
 *
 * <p>Atencao a versao: no Testcontainers 2.x a classe mudou de pacote
 * ({@code org.testcontainers.postgresql.PostgreSQLContainer}, antes
 * {@code org.testcontainers.containers}) e deixou de ser generica. Praticamente
 * todo exemplo online ainda mostra a forma antiga, com
 * {@code PostgreSQLContainer<?>}.</p>
 */
@TestConfiguration(proxyBeanMethods = false)
public class PostgresDeTeste {

    // Versao fixada: "latest" faria a suite quebrar sozinha no dia em que o
    // projeto Postgres publicasse uma imagem nova.
    private static final PostgreSQLContainer CONTAINER =
            new PostgreSQLContainer(DockerImageName.parse("postgres:17-alpine"));

    static {
        CONTAINER.start();
    }

    /**
     * {@code @ServiceConnection} dispensa configurar url, usuario e senha na mao:
     * o Spring Boot le esses dados do proprio container.
     */
    @Bean(destroyMethod = "")
    @ServiceConnection
    PostgreSQLContainer postgres() {
        return CONTAINER;
    }
}
