package com.joaoalcantara.pedidos.comum;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;

import org.springframework.test.context.DynamicPropertyRegistry;

/**
 * Um Stripe de mentira, servindo HTTP de verdade.
 *
 * <p>Sobe numa porta livre e registra a propria URL em
 * {@code pedidos.gateway.url}, para que o adaptador aponte para ele sem saber
 * que e um dublê. O que fica exercitado, assim, e o caminho inteiro: montagem do
 * corpo form-encoded, cabecalhos, codigo de status, desserializacao da resposta
 * e comportamento no timeout.</p>
 *
 * <p>A alternativa — {@code MockRestServiceServer} — intercepta antes do socket.
 * Seria mais rapida, mas um erro de serializacao ou de cabecalho passaria
 * despercebido, que e justamente o tipo de defeito que integracao com terceiro
 * produz.</p>
 */
public final class GatewayFalso {

    private final WireMockServer servidor;

    public GatewayFalso() {
        // dynamicPort: duas execucoes em paralelo (ou uma porta ocupada por outro
        // processo) nao derrubam a suite.
        this.servidor = new WireMockServer(WireMockConfiguration.options().dynamicPort());
    }

    public void iniciar(DynamicPropertyRegistry propriedades) {
        servidor.start();
        propriedades.add("pedidos.gateway.url", servidor::baseUrl);
    }

    public void parar() {
        servidor.stop();
    }

    public void limpar() {
        servidor.resetAll();
    }

    public WireMockServer servidor() {
        return servidor;
    }

    /** Corpo de um PaymentIntent, com os campos que o adaptador le. */
    public static String intentJson(String id, String status) {
        return """
                {
                  "id": "%s",
                  "object": "payment_intent",
                  "status": "%s",
                  "amount": 30000,
                  "currency": "brl",
                  "client_secret": "%s_secret_abc123"
                }
                """.formatted(id, status, id);
    }
}
