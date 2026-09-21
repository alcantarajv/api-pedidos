package com.joaoalcantara.pedidos.consumo.dominio;

import java.util.List;

/** Porta de persistencia das notificacoes. */
public interface NotificacaoRepositorio {

    Notificacao salvar(Notificacao notificacao);

    List<Notificacao> doPedido(Long pedidoId);
}
