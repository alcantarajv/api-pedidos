package com.joaoalcantara.pedidos.pagamento.dominio;

import java.math.BigDecimal;

/**
 * Porta de saida para o gateway de pagamento.
 *
 * <p>O dominio conversa com esta interface, nao com o Stripe. Tres consequencias
 * praticas:</p>
 *
 * <ul>
 *   <li>Trocar de gateway (Mercado Pago, por exemplo) e escrever outro
 *       adaptador em {@code pagamento.infra}, sem tocar em regra de negocio.</li>
 *   <li>Os testes de regra usam um duble em memoria e nao precisam de rede.</li>
 *   <li>O vocabulario do fornecedor — {@code payment_intent},
 *       {@code client_secret}, centavos como inteiro — fica confinado ao
 *       adaptador.</li>
 * </ul>
 */
public interface GatewayDePagamento {

    /**
     * Cria a cobranca no gateway.
     *
     * @param chaveIdempotencia repassada ao gateway para que um reenvio desta
     *        chamada nao gere duas cobrancas. A mesma ideia que a API expoe ao
     *        cliente, aplicada um nivel acima — sem ela, uma repeticao nossa
     *        cobra o cliente duas vezes.
     */
    Cobranca criar(String referenciaDoPedido, BigDecimal valor, String chaveIdempotencia);

    /** Le no gateway o estado atual da cobranca. */
    Cobranca consultar(String idExterno);

    /**
     * Uma cobranca, como o gateway a descreve.
     *
     * @param segredoDoCliente credencial de uso unico que o front-end usa para
     *        concluir o pagamento. Nao e segredo do servidor: vale so para
     *        aquela cobranca, e sem ele o cliente nao tem como pagar. Por isso
     *        nao e persistido — quando precisamos dele de novo, perguntamos ao
     *        gateway.
     */
    record Cobranca(String idExterno, StatusPagamento status, String segredoDoCliente) {
    }
}
