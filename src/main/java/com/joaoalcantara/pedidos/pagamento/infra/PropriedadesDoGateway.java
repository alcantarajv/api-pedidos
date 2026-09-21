package com.joaoalcantara.pedidos.pagamento.infra;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Configuracao do gateway de pagamento.
 *
 * <p>A chave da API vem de variavel de ambiente e nunca do repositorio — com
 * ela, qualquer pessoa movimenta a conta do Stripe. A aplicacao recusa subir sem
 * ela, pelo mesmo motivo do segredo do JWT: melhor falhar no boot, dizendo o que
 * falta, do que subir e quebrar na primeira cobranca.</p>
 *
 * <p>Os timeouts sao explicitos porque o padrao e nao ter nenhum. Um gateway que
 * aceita a conexao e nunca responde prenderia a thread da requisicao
 * indefinidamente; algumas dessas e o pool do Tomcat acaba, e a API inteira para
 * de responder por causa de um fornecedor lento.</p>
 */
@Validated
@ConfigurationProperties(prefix = "pedidos.gateway")
public record PropriedadesDoGateway(
        @NotBlank(message = "defina a URL base do gateway") String url,
        @NotBlank(message = "defina a variavel de ambiente STRIPE_API_KEY") String chaveApi,
        @NotNull Duration timeoutDeConexao,
        @NotNull Duration timeoutDeLeitura) {
}
