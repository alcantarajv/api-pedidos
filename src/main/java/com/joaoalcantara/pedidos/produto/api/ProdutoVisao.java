package com.joaoalcantara.pedidos.produto.api;

/**
 * O que a API devolve sobre um produto, em duas versoes.
 *
 * <p>{@link ProdutoResposta} e a visao publica; {@link ProdutoAdminResposta}
 * acrescenta as parcelas internas do estoque. A separacao existe porque
 * {@code estoqueReservado} conta ao visitante quantos pedidos pendentes a loja
 * tem — informacao comercial que nao e dele. O cliente ve quantas unidades pode
 * comprar; quanto esta preso em pedidos aguardando pagamento e assunto de quem
 * administra.</p>
 *
 * <p>Interface selada para que o compilador garanta que so existem estas duas
 * visoes: acrescentar uma terceira exige declara-la aqui, e nao acontece por
 * descuido em algum controller distante.</p>
 */
public sealed interface ProdutoVisao permits ProdutoResposta, ProdutoAdminResposta {
}
