package com.joaoalcantara.pedidos.webhook.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.joaoalcantara.pedidos.webhook.aplicacao.CobrancaDesconhecidaException;
import com.joaoalcantara.pedidos.webhook.aplicacao.WebhookServico;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Endpoint que o gateway chama quando o estado de uma cobranca muda.
 *
 * <p>Publico na configuracao de seguranca, porque nao existe token de usuario
 * numa chamada feita por outro servidor. Quem autentica a requisicao e a
 * assinatura HMAC do corpo — e e por isso que a verificacao dela e a primeira
 * coisa que acontece.</p>
 *
 * <p>O corpo chega como {@code String}, nao como objeto. A assinatura e
 * calculada sobre os bytes exatos recebidos: deixar o Spring desserializar antes
 * e serializar de novo para conferir mudaria espacos e ordem de campos, e a
 * assinatura deixaria de bater por um motivo invisivel no JSON.</p>
 */
@Tag(name = "Webhook", description = "Notificacoes do gateway de pagamento")
@RestController
@RequestMapping("/api/webhooks/gateway")
public class WebhookController {

    private static final Logger log = LoggerFactory.getLogger(WebhookController.class);

    private final WebhookServico servico;

    public WebhookController(WebhookServico servico) {
        this.servico = servico;
    }

    @Operation(summary = "Recebe um evento do gateway",
            description = """
                    Chamado pelo gateway, nao por clientes da API. Nao usa token: quem autentica a
                    requisicao e a assinatura HMAC do corpo, no cabecalho `Stripe-Signature`.

                    **A entrega e \"pelo menos uma vez\"** — o mesmo evento chega mais de uma vez, por
                    definicao. O identificador do evento e gravado numa tabela com restricao de
                    unicidade, na MESMA transacao que aplica o efeito: a segunda entrega colide e a
                    transacao inteira e desfeita, sem efeito duplicado.

                    Responde 200 tanto no processamento quanto na repeticao — e o que faz o gateway
                    parar de reenviar. Erros de verdade devolvem 4xx/5xx de proposito, para que ele
                    reentregue.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Evento processado, repetido, ou de cobranca desconhecida"),
            @ApiResponse(responseCode = "400", description = "Corpo fora do formato esperado", content = @Content),
            @ApiResponse(responseCode = "401", description = "Assinatura ausente, invalida ou vencida", content = @Content),
            @ApiResponse(responseCode = "422", description = "O evento nao pode ser aplicado (pedido cancelado, por exemplo): o gateway deve reentregar", content = @Content)
    })
    @SecurityRequirements
    @PostMapping
    public ResponseEntity<Void> receber(@RequestBody String corpoCru,
                                        @Parameter(description = "Assinatura HMAC-SHA256 no formato t=<carimbo>,v1=<hmac>", required = true)
                                        @RequestHeader(name = "Stripe-Signature", required = false) String assinatura) {
        try {
            WebhookServico.Resultado resultado = servico.receber(corpoCru, assinatura);
            log.debug("Webhook recebido: {}", resultado);
        } catch (CobrancaDesconhecidaException e) {
            // 200 de proposito. Reenviar nao vai fazer a cobranca existir aqui, e
            // um 4xx faria o gateway repetir a entrega por horas e depois marcar o
            // endpoint como problematico.
            log.warn("Webhook ignorado: {}", e.getMessage());
        }

        // 200 sem corpo: e o que faz o gateway parar de reenviar. Qualquer erro
        // nosso que escape daqui vira 4xx/5xx e provoca nova entrega — que e
        // justamente o comportamento desejado quando o processamento falhou.
        return ResponseEntity.ok().build();
    }
}
