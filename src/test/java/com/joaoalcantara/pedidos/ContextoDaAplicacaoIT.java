package com.joaoalcantara.pedidos;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import com.joaoalcantara.pedidos.comum.PostgresDeTeste;
import com.joaoalcantara.pedidos.comum.RabbitDeTeste;

/**
 * Prova que o scaffold sobe de pe: contexto carregado, banco conectado e broker
 * conectado.
 *
 * <p>Parece um teste trivial, e e — mas e o unico que falha cedo quando uma
 * dependencia de infraestrutura para de subir. Sem ele, um erro de configuracao
 * do RabbitMQ so apareceria na etapa do outbox, misturado com a logica que
 * deveria estar sendo testada ali.</p>
 *
 * <p>Sufixo {@code IT}: roda no {@code mvnw verify} pelo failsafe, e exige
 * Docker. O {@code mvnw test} continua rapido, so com os unitarios.</p>
 */
@SpringBootTest
@Import({PostgresDeTeste.class, RabbitDeTeste.class})
class ContextoDaAplicacaoIT {

    @Autowired
    private DataSource dataSource;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Test
    void conecta_no_postgres() throws Exception {
        try (Connection conexao = dataSource.getConnection()) {
            assertThat(conexao.isValid(2)).isTrue();
            assertThat(conexao.getMetaData().getDatabaseProductName()).isEqualTo("PostgreSQL");
        }
    }

    @Test
    void conecta_no_rabbitmq() {
        Boolean canalAberto = rabbitTemplate.execute(canal -> canal.isOpen());

        assertThat(canalAberto).isTrue();
    }
}
