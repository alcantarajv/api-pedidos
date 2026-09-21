package com.joaoalcantara.pedidos.pagamento.dominio;

/**
 * Situacao da cobranca no gateway, traduzida para o vocabulario deste sistema.
 *
 * <p>Os nomes sao nossos de proposito. O Stripe fala em
 * {@code requires_payment_method}, {@code processing}, {@code succeeded}; se
 * esses nomes entrassem no dominio, trocar de gateway viraria uma reescrita, e
 * cada regra de negocio passaria a depender do vocabulario de um fornecedor.</p>
 */
public enum StatusPagamento {
    /** Cobranca criada, aguardando o cliente concluir o pagamento. */
    PENDENTE,
    /** O gateway confirmou o recebimento. */
    APROVADO,
    /** O gateway recusou (cartao negado, por exemplo). */
    RECUSADO,
    /** A cobranca foi cancelada antes de ser paga. */
    CANCELADO;

    public boolean ehFinal() {
        return this != PENDENTE;
    }
}
