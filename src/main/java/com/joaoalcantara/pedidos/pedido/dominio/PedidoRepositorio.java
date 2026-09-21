package com.joaoalcantara.pedidos.pedido.dominio;

import java.util.List;
import java.util.Optional;

/** Porta de persistencia do agregado Pedido. */
public interface PedidoRepositorio {

    Pedido salvar(Pedido pedido);

    Optional<Pedido> porId(Long id);

    /** Carrega o pedido ja com os itens, evitando N+1 na montagem da resposta. */
    Optional<Pedido> porIdComItens(Long id);

    /**
     * Busca o pedido que ja foi criado com aquela chave, para aquele usuario.
     *
     * <p>E o que transforma um reenvio em consulta: mesma chave, mesmo pedido,
     * nenhum efeito novo.</p>
     */
    Optional<Pedido> porChaveDeIdempotencia(Long usuarioId, String chave);

    List<Pedido> listarDoUsuario(Long usuarioId);

    List<Pedido> listarTodos();
}
