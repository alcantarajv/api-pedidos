package com.joaoalcantara.pedidos.pedido.aplicacao;

import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.joaoalcantara.pedidos.comum.erro.RecursoNaoEncontradoException;
import com.joaoalcantara.pedidos.pedido.dominio.ItemPedido;
import com.joaoalcantara.pedidos.pedido.dominio.Pedido;
import com.joaoalcantara.pedidos.pedido.dominio.PedidoRepositorio;
import com.joaoalcantara.pedidos.produto.dominio.Produto;
import com.joaoalcantara.pedidos.produto.dominio.ProdutoRepositorio;

/**
 * Cancela o pedido e devolve ao catalogo o estoque que ele segurava.
 *
 * <p>As duas coisas acontecem na mesma transacao. Se o cancelamento gravasse e a
 * devolucao falhasse, as unidades ficariam presas a um pedido morto — estoque
 * fantasma, que so aparece quando alguem conta as caixas no deposito.</p>
 */
@Component
class CanceladorDePedido {

    private final PedidoRepositorio pedidos;
    private final ProdutoRepositorio produtos;

    CanceladorDePedido(PedidoRepositorio pedidos, ProdutoRepositorio produtos) {
        this.pedidos = pedidos;
        this.produtos = produtos;
    }

    @Transactional
    Pedido cancelar(Long pedidoId) {
        // Trava a linha do pedido: uma transicao de estado e um ler-decidir-gravar,
        // e sem serializacao dois cancelamentos simultaneos passam os dois pela
        // validacao.
        //
        // Hoje o estoque sobreviveria mesmo sem esta trava — a devolucao trava o
        // produto, e devolverReserva recusa devolver mais do que esta reservado.
        // Mas isso e sorte do efeito colateral que esta transicao tem: uma
        // transicao sem guarda propria (publicar um evento, por exemplo, como na
        // Etapa 8) aconteceria duas vezes. A trava faz a garantia valer para
        // qualquer transicao, nao so para esta.
        Pedido pedido = pedidos.porIdComTrava(pedidoId)
                .orElseThrow(() -> RecursoNaoEncontradoException.de("Pedido", pedidoId));

        boolean precisaDevolverEstoque = pedido.mantemReservaDeEstoque();

        // A transicao e validada pela entidade antes de qualquer efeito colateral:
        // um pedido ja cancelado para aqui, sem devolver estoque uma segunda vez.
        pedido.cancelar();

        if (precisaDevolverEstoque) {
            devolverEstoque(pedido);
        }
        return pedidos.salvar(pedido);
    }

    /** Mesma ordem de travamento da criacao, pelo mesmo motivo: evitar deadlock. */
    private void devolverEstoque(Pedido pedido) {
        List<ItemPedido> itens = pedido.getItens().stream()
                .sorted(Comparator.comparing(ItemPedido::getProdutoId))
                .toList();

        for (ItemPedido item : itens) {
            Produto produto = produtos.porIdComTrava(item.getProdutoId())
                    .orElseThrow(() -> RecursoNaoEncontradoException.de("Produto", item.getProdutoId()));
            produto.devolverReserva(item.getQuantidade());
            produtos.salvar(produto);
        }
    }
}
