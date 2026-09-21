package com.joaoalcantara.pedidos.pagamento.api;

import java.math.BigDecimal;
import java.time.Instant;

import com.joaoalcantara.pedidos.pagamento.dominio.Pagamento;
import com.joaoalcantara.pedidos.pagamento.dominio.StatusPagamento;

/**
 * Estado da cobranca.
 *
 * <p>O {@code idExterno} aparece de proposito: e por ele que o cliente localiza
 * a cobranca no painel do gateway quando algo nao bate. O {@code segredoDoCliente}
 * so existe na resposta da criacao, porque so o front-end que esta concluindo
 * aquele pagamento precisa dele — nao ha motivo para reexibi-lo em consultas.</p>
 */
public record PagamentoResposta(
        Long pedidoId,
        String idExterno,
        StatusPagamento status,
        BigDecimal valor,
        Instant atualizadoEm,
        String segredoDoCliente) {

    public static PagamentoResposta de(Pagamento pagamento) {
        return new PagamentoResposta(pagamento.getPedidoId(), pagamento.getIdExterno(),
                pagamento.getStatus(), pagamento.getValor(), pagamento.getAtualizadoEm(), null);
    }

    public static PagamentoResposta comSegredo(Pagamento pagamento, String segredoDoCliente) {
        return new PagamentoResposta(pagamento.getPedidoId(), pagamento.getIdExterno(),
                pagamento.getStatus(), pagamento.getValor(), pagamento.getAtualizadoEm(), segredoDoCliente);
    }
}
