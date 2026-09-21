package com.joaoalcantara.pedidos.comum;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * Marca um teste de integracao: sobe a aplicacao inteira contra um PostgreSQL e
 * um RabbitMQ de verdade.
 *
 * <p>Anotacao composta para que nenhuma classe de teste repita a configuracao
 * dos containers — e para que trocar essa configuracao seja uma edicao em um
 * lugar so.</p>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest
@ActiveProfiles("test")
@Import({PostgresDeTeste.class, RabbitDeTeste.class, FilaDeTeste.class})
public @interface TesteDeIntegracao {
}
