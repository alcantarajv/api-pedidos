package com.joaoalcantara.pedidos.pagamento.dominio;

import java.util.Optional;

/** Porta de persistencia dos pagamentos. */
public interface PagamentoRepositorio {

    Pagamento salvar(Pagamento pagamento);

    Optional<Pagamento> porPedido(Long pedidoId);

    /** Usado na Etapa 7 para achar o pedido a partir do evento do gateway. */
    Optional<Pagamento> porIdExterno(String idExterno);
}
