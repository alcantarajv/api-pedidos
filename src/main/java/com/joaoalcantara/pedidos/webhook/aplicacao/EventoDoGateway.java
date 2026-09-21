package com.joaoalcantara.pedidos.webhook.aplicacao;

/**
 * Um evento do gateway, reduzido ao que este sistema precisa saber.
 *
 * <p>O JSON do Stripe traz dezenas de campos. Extrair so estes quatro no limite
 * da aplicacao evita que o formato do fornecedor se espalhe pelo codigo — e
 * torna obvio, para quem le, de que informacao a regra realmente depende.</p>
 *
 * @param id identificador do evento; a chave da idempotencia
 * @param tipo {@code payment_intent.succeeded} e afins
 * @param idDaCobranca identificador do PaymentIntent, que liga o evento ao pedido
 */
public record EventoDoGateway(String id, String tipo, String idDaCobranca) {

    public static final String PAGAMENTO_APROVADO = "payment_intent.succeeded";
    public static final String PAGAMENTO_RECUSADO = "payment_intent.payment_failed";
    public static final String PAGAMENTO_CANCELADO = "payment_intent.canceled";
}
