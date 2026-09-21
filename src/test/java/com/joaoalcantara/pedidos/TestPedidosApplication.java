package com.joaoalcantara.pedidos;

import org.springframework.boot.SpringApplication;

import com.joaoalcantara.pedidos.comum.PostgresDeTeste;
import com.joaoalcantara.pedidos.comum.RabbitDeTeste;

/**
 * Sobe a aplicacao localmente com PostgreSQL e RabbitMQ descartaveis.
 *
 * <p>Atalho de desenvolvimento: rodar esta classe pela IDE dispensa o
 * {@code docker compose up} e a configuracao manual de conexao. As duas
 * dependencias morrem com o processo, entao cada execucao comeca limpa.</p>
 */
public class TestPedidosApplication {

    public static void main(String[] args) {
        SpringApplication.from(PedidosApplication::main)
                .with(PostgresDeTeste.class, RabbitDeTeste.class)
                .run(args);
    }
}
