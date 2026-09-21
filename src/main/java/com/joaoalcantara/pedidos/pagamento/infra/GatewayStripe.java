package com.joaoalcantara.pedidos.pagamento.infra;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.SocketTimeoutException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.joaoalcantara.pedidos.comum.erro.GatewayIndisponivelException;
import com.joaoalcantara.pedidos.pagamento.dominio.GatewayDePagamento;
import com.joaoalcantara.pedidos.pagamento.dominio.StatusPagamento;

/**
 * Adaptador do Stripe, falando HTTP direto com a API de PaymentIntents.
 *
 * <p>Nao usamos o SDK oficial. Ele daria modelos tipados e retentativas
 * prontas, mas esconderia justamente o que este projeto quer deixar visivel: o
 * corpo form-encoded, o cabecalho de idempotencia, o timeout. Sao duas chamadas
 * ({@code POST /v1/payment_intents} e {@code GET /v1/payment_intents/:id}), e o
 * custo de escreve-las e menor que o de carregar uma dependencia grande que
 * ninguem nesta base saberia depurar.</p>
 *
 * <p>Detalhe que surpreende quem integra com o Stripe pela primeira vez: a API
 * e <b>form-encoded</b>, nao JSON, mesmo respondendo JSON.</p>
 */
@Component
class GatewayStripe implements GatewayDePagamento {

    private static final Logger log = LoggerFactory.getLogger(GatewayStripe.class);

    private static final String MOEDA = "brl";

    private final RestClient http;

    GatewayStripe(RestClient.Builder builder, PropriedadesDoGateway propriedades) {
        var fabrica = new SimpleClientHttpRequestFactory();
        fabrica.setConnectTimeout(propriedades.timeoutDeConexao());
        fabrica.setReadTimeout(propriedades.timeoutDeLeitura());

        this.http = builder
                .baseUrl(propriedades.url())
                .requestFactory(fabrica)
                // A chave viaja em todo request. Configurada uma vez aqui, nao ha
                // caminho no codigo que esqueca de envia-la.
                .defaultHeader("Authorization", "Bearer " + propriedades.chaveApi())
                .build();
    }

    @Override
    public Cobranca criar(String referenciaDoPedido, BigDecimal valor, String chaveIdempotencia) {
        MultiValueMap<String, String> corpo = new LinkedMultiValueMap<>();
        corpo.add("amount", String.valueOf(emCentavos(valor)));
        corpo.add("currency", MOEDA);
        // Devolve o pedido no evento do webhook, sem precisar de outra consulta.
        corpo.add("metadata[pedido_id]", referenciaDoPedido);
        corpo.add("automatic_payment_methods[enabled]", "true");

        RespostaDoIntent resposta = executar(() -> http.post()
                .uri("/v1/payment_intents")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                // Idempotencia do lado do gateway: se esta chamada for repetida —
                // por retentativa nossa ou por timeout —, o Stripe devolve a
                // cobranca ja criada em vez de cobrar o cliente de novo.
                .header("Idempotency-Key", chaveIdempotencia)
                .body(corpo)
                .retrieve()
                .body(RespostaDoIntent.class));

        return new Cobranca(resposta.id(), traduzir(resposta.status()), resposta.segredoDoCliente());
    }

    @Override
    public Cobranca consultar(String idExterno) {
        RespostaDoIntent resposta = executar(() -> http.get()
                .uri("/v1/payment_intents/{id}", idExterno)
                .retrieve()
                .body(RespostaDoIntent.class));

        return new Cobranca(resposta.id(), traduzir(resposta.status()), resposta.segredoDoCliente());
    }

    /**
     * Converte qualquer falha de comunicacao numa excecao de dominio.
     *
     * <p>Sem isto, um {@code SocketTimeoutException} subiria ate o
     * {@code @RestControllerAdvice} e viraria 500 — dizendo ao cliente que o
     * defeito e nosso, quando o problema e o fornecedor estar fora do ar.</p>
     */
    private <T> T executar(java.util.function.Supplier<T> chamada) {
        try {
            T resposta = chamada.get();
            if (resposta == null) {
                throw new GatewayIndisponivelException("O gateway respondeu sem corpo");
            }
            return resposta;
        } catch (RestClientResponseException e) {
            // Respondeu, mas com erro. O corpo do Stripe traz a causa; ele vai para
            // o log e NAO para a resposta: pode conter detalhe de configuracao da
            // conta, que nao e assunto de quem comprou.
            log.warn("Gateway de pagamento respondeu {}: {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new GatewayIndisponivelException(
                    "O gateway de pagamento recusou a requisicao (HTTP %d)".formatted(e.getStatusCode().value()));
        } catch (RestClientException e) {
            // Tudo o mais que impede uma resposta utilizavel: conexao recusada,
            // DNS, timeout, corpo ilegivel.
            //
            // Nao basta capturar ResourceAccessException: quando o tempo estoura
            // no MEIO da leitura do corpo, o RestClient embrulha o
            // SocketTimeoutException num RestClientException generico, e um catch
            // mais estreito deixaria o erro vazar como 500.
            boolean tempoEsgotado = temCausa(e, SocketTimeoutException.class);
            log.warn("Falha de comunicacao com o gateway de pagamento ({}): {}",
                    tempoEsgotado ? "timeout" : "erro de transporte", e.getMessage());
            throw new GatewayIndisponivelException(tempoEsgotado
                    ? "O gateway de pagamento nao respondeu a tempo"
                    : "O gateway de pagamento nao respondeu de forma utilizavel");
        }
    }

    private static boolean temCausa(Throwable erro, Class<? extends Throwable> procurada) {
        for (Throwable causa = erro; causa != null; causa = causa.getCause()) {
            if (procurada.isInstance(causa)) {
                return true;
            }
            if (causa == causa.getCause()) {
                break;
            }
        }
        return false;
    }

    /**
     * O Stripe trabalha com a menor unidade da moeda — centavos, em inteiro.
     *
     * <p>{@code R$ 300,00} vira {@code 30000}. Mandar {@code 300} cobraria tres
     * reais; mandar {@code 300.00} nem e aceito. Como o valor ja tem escala 2,
     * o inteiro sem escala do {@code BigDecimal} <b>e</b> o total em centavos —
     * sem multiplicacao por 100 e sem ponto flutuante no meio.</p>
     */
    private static long emCentavos(BigDecimal valor) {
        return valor.setScale(2, RoundingMode.UNNECESSARY).unscaledValue().longValueExact();
    }

    /**
     * Traduz o vocabulario do Stripe para o do dominio.
     *
     * <p>Repare que {@code RECUSADO} nao aparece: o PaymentIntent do Stripe nao
     * tem esse estado. Uma tentativa negada devolve o intent para
     * {@code requires_payment_method}, e a recusa chega como o evento
     * {@code payment_intent.payment_failed} — assunto do webhook, na Etapa 7.</p>
     *
     * <p>Status desconhecido vira {@code PENDENTE} com um aviso no log, em vez
     * de excecao: o Stripe pode introduzir estados novos, e derrubar a
     * requisicao do cliente por causa disso seria pior do que esperar.</p>
     */
    private StatusPagamento traduzir(String statusDoStripe) {
        return switch (statusDoStripe) {
            case "succeeded" -> StatusPagamento.APROVADO;
            case "canceled" -> StatusPagamento.CANCELADO;
            case "requires_payment_method", "requires_confirmation", "requires_action",
                 "requires_capture", "processing" -> StatusPagamento.PENDENTE;
            default -> {
                log.warn("Status desconhecido vindo do gateway: '{}'. Tratando como PENDENTE.", statusDoStripe);
                yield StatusPagamento.PENDENTE;
            }
        };
    }

    /** Só os campos que usamos; o Stripe devolve dezenas de outros. */
    record RespostaDoIntent(
            String id,
            String status,
            @JsonProperty("client_secret") String segredoDoCliente) {
    }
}
