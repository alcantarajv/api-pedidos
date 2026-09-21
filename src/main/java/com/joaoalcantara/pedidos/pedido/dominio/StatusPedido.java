package com.joaoalcantara.pedidos.pedido.dominio;

import java.util.Set;

/**
 * Situacao do pedido e as transicoes que ela permite.
 *
 * <pre>
 * AGUARDANDO_PAGAMENTO --&gt; PAGO --&gt; SEPARANDO --&gt; ENVIADO --&gt; ENTREGUE
 *          |                |
 *          --&gt; CANCELADO    --&gt; REEMBOLSADO
 * </pre>
 *
 * <p>O grafo vive aqui, e nao numa tabela de configuracao nem espalhado em
 * {@code if} pelos servicos: quem le o enum ve o ciclo de vida inteiro numa
 * tela, e acrescentar um estado obriga a declarar de onde se chega nele.</p>
 *
 * <p>Os conjuntos sao devolvidos por um {@code switch} em vez de preenchidos no
 * construtor porque uma constante de enum nao pode referenciar outra enquanto
 * as constantes ainda estao sendo criadas — a tentativa compila e falha em
 * tempo de execucao com {@code NullPointerException} na inicializacao da
 * classe.</p>
 */
public enum StatusPedido {
    AGUARDANDO_PAGAMENTO,
    PAGO,
    SEPARANDO,
    ENVIADO,
    ENTREGUE,
    CANCELADO,
    REEMBOLSADO;

    /** Estados alcancaveis a partir deste. Vazio significa estado final. */
    public Set<StatusPedido> proximos() {
        return switch (this) {
            case AGUARDANDO_PAGAMENTO -> Set.of(PAGO, CANCELADO);
            case PAGO -> Set.of(SEPARANDO, REEMBOLSADO);
            case SEPARANDO -> Set.of(ENVIADO);
            case ENVIADO -> Set.of(ENTREGUE);
            case ENTREGUE, CANCELADO, REEMBOLSADO -> Set.of();
        };
    }

    public boolean permiteIrPara(StatusPedido destino) {
        return proximos().contains(destino);
    }

    /** Estado final: o pedido acabou, para bem ou para mal. */
    public boolean ehFinal() {
        return proximos().isEmpty();
    }
}
