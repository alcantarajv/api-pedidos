package com.joaoalcantara.pedidos.webhook.infra;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.stereotype.Component;

import com.joaoalcantara.pedidos.pagamento.infra.PropriedadesDoGateway;
import com.joaoalcantara.pedidos.webhook.dominio.AssinaturaInvalidaException;

/**
 * Verifica que a chamada veio mesmo do gateway, no esquema do Stripe.
 *
 * <p>O cabecalho tem a forma {@code t=1690000000,v1=5257a8...}: um carimbo de
 * tempo e um HMAC-SHA256 sobre {@code "<t>.<corpo cru>"}, com o segredo do
 * webhook como chave. Quem nao tem o segredo nao consegue produzir o HMAC.</p>
 *
 * <p>Tres detalhes que parecem preciosismo e nao sao:</p>
 *
 * <ol>
 *   <li><b>O corpo precisa ser o cru</b>, byte a byte. Desserializar e
 *       serializar de novo muda espacos e ordem de campos, e a assinatura passa
 *       a nao bater por um motivo que ninguem descobre olhando o JSON.</li>
 *   <li><b>A comparacao e em tempo constante.</b> Um {@code equals} comum sai
 *       no primeiro byte diferente, e o tempo de resposta vaza quantos bytes
 *       iniciais estavam certos — o que permite descobrir a assinatura correta
 *       byte a byte.</li>
 *   <li><b>O carimbo de tempo tem tolerancia.</b> Sem ela, uma requisicao
 *       valida capturada hoje continuaria valida para sempre: bastaria
 *       reenviar a mesma mensagem, com a mesma assinatura, para reprocessar o
 *       evento.</li>
 * </ol>
 */
@Component
public class VerificadorDeAssinatura {

    private static final String ALGORITMO = "HmacSHA256";

    private final PropriedadesDoGateway propriedades;
    private final Clock relogio;

    public VerificadorDeAssinatura(PropriedadesDoGateway propriedades, Clock relogio) {
        this.propriedades = propriedades;
        this.relogio = relogio;
    }

    /**
     * @param corpoCru exatamente os bytes recebidos, sem passar por nenhum
     *        parser antes
     * @param cabecalho o conteudo de {@code Stripe-Signature}
     */
    public void verificar(String corpoCru, String cabecalho) {
        if (cabecalho == null || cabecalho.isBlank()) {
            throw new AssinaturaInvalidaException("Requisicao sem cabecalho de assinatura");
        }

        Cabecalho partes = Cabecalho.analisar(cabecalho);
        exigirDentroDaJanela(partes.carimbo());

        String esperada = calcularHmac(partes.carimbo() + "." + corpoCru);

        if (!iguaisEmTempoConstante(esperada, partes.assinatura())) {
            throw new AssinaturaInvalidaException("Assinatura nao confere");
        }
    }

    private void exigirDentroDaJanela(String carimbo) {
        Instant momentoDoEvento;
        try {
            momentoDoEvento = Instant.ofEpochSecond(Long.parseLong(carimbo));
        } catch (NumberFormatException e) {
            throw new AssinaturaInvalidaException("Carimbo de tempo invalido na assinatura");
        }

        Duration diferenca = Duration.between(momentoDoEvento, relogio.instant()).abs();
        if (diferenca.compareTo(propriedades.toleranciaDoWebhook()) > 0) {
            throw new AssinaturaInvalidaException("Assinatura fora da janela de tolerancia");
        }
    }

    private String calcularHmac(String conteudo) {
        try {
            Mac mac = Mac.getInstance(ALGORITMO);
            mac.init(new SecretKeySpec(propriedades.segredoDoWebhook().getBytes(StandardCharsets.UTF_8), ALGORITMO));
            return HexFormat.of().formatHex(mac.doFinal(conteudo.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.GeneralSecurityException e) {
            // Algoritmo ausente ou chave invalida: erro de configuracao, nao de
            // requisicao. Nao pode virar "assinatura invalida", ou uma configuracao
            // quebrada pareceria ataque.
            throw new IllegalStateException("Falha ao calcular o HMAC do webhook", e);
        }
    }

    /**
     * Comparacao que sempre percorre os dois valores inteiros.
     *
     * <p>{@code MessageDigest.isEqual} faz isso; {@code String.equals} nao.</p>
     */
    private static boolean iguaisEmTempoConstante(String esperada, String recebida) {
        return MessageDigest.isEqual(
                esperada.getBytes(StandardCharsets.UTF_8),
                recebida.getBytes(StandardCharsets.UTF_8));
    }

    /** As partes do cabecalho {@code t=...,v1=...}. */
    private record Cabecalho(String carimbo, String assinatura) {

        static Cabecalho analisar(String cabecalho) {
            String carimbo = null;
            String assinatura = null;

            for (String parte : cabecalho.split(",")) {
                String[] chaveValor = parte.trim().split("=", 2);
                if (chaveValor.length != 2) {
                    continue;
                }
                switch (chaveValor[0]) {
                    case "t" -> carimbo = chaveValor[1];
                    // O Stripe pode mandar mais de um v1 durante rotacao de
                    // segredo; ficamos com o primeiro, que e o do segredo ativo.
                    case "v1" -> assinatura = assinatura == null ? chaveValor[1] : assinatura;
                    default -> {
                        // v0 e outros esquemas: ignorados de proposito.
                    }
                }
            }

            if (carimbo == null || assinatura == null) {
                throw new AssinaturaInvalidaException("Cabecalho de assinatura mal formado");
            }
            return new Cabecalho(carimbo, assinatura);
        }
    }
}
