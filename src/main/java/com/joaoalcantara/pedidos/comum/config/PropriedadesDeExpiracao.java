package com.joaoalcantara.pedidos.comum.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * Quanto tempo um pedido sobrevive sem pagamento.
 *
 * @param prazo janela que o cliente tem para pagar. Nao e um numero tecnico: e
 *        politica comercial. Curto demais irrita quem foi buscar o cartao;
 *        longo demais mantem estoque preso a pedidos que ninguem vai pagar,
 *        negando venda a quem compraria agora.
 * @param intervaloMs de quanto em quanto tempo o job procura vencidos. Como a
 *        expiracao nao precisa ser instantanea, um intervalo folgado evita
 *        consultar o banco a toa.
 * @param tamanhoDoLote quantos pedidos por rodada. Limita o estrago de um pico:
 *        mil pedidos vencendo no mesmo minuto viram varias rodadas curtas em
 *        vez de uma transacao gigante.
 */
@Validated
@ConfigurationProperties(prefix = "pedidos.expiracao")
public record PropriedadesDeExpiracao(
        @NotNull Duration prazo,
        @Positive long intervaloMs,
        @Positive int tamanhoDoLote) {
}
