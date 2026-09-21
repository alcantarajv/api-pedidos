package com.joaoalcantara.pedidos.comum;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * PostgreSQL real para os testes de integracao.
 *
 * <p>Banco em memoria (H2) nao serviria: a idempotencia deste projeto depende de
 * uma violacao de restricao de unicidade acontecer exatamente como acontece em
 * producao, e o codigo de erro que a aplicacao traduz e do PostgreSQL. Testar
 * contra outro banco validaria um sistema diferente do que roda de verdade.</p>
 *
 * <p>{@code @ServiceConnection} dispensa configurar url, usuario e senha na mao:
 * o Spring Boot le esses dados do proprio container.</p>
 *
 * <p>Atencao a versao: no Testcontainers 2.x a classe mudou de pacote
 * ({@code org.testcontainers.postgresql.PostgreSQLContainer}, antes
 * {@code org.testcontainers.containers}) e deixou de ser generica. Praticamente
 * todo exemplo online ainda mostra a forma antiga, com
 * {@code PostgreSQLContainer<?>}.</p>
 */
@TestConfiguration(proxyBeanMethods = false)
public class PostgresDeTeste {

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgres() {
        // Versao fixada: "latest" faria a suite quebrar sozinha no dia em que o
        // projeto Postgres publicasse uma imagem nova.
        return new PostgreSQLContainer(DockerImageName.parse("postgres:17-alpine"));
    }
}
