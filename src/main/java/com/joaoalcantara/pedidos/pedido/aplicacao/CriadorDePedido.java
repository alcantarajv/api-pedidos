package com.joaoalcantara.pedidos.pedido.aplicacao;

import java.time.Clock;
import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.joaoalcantara.pedidos.comum.erro.RecursoNaoEncontradoException;
import com.joaoalcantara.pedidos.comum.erro.RegraDeNegocioException;
import com.joaoalcantara.pedidos.pedido.api.ItemRequisicao;
import com.joaoalcantara.pedidos.pedido.api.PedidoRequisicao;
import com.joaoalcantara.pedidos.pedido.dominio.Pedido;
import com.joaoalcantara.pedidos.pedido.dominio.PedidoRepositorio;
import com.joaoalcantara.pedidos.produto.dominio.Produto;
import com.joaoalcantara.pedidos.produto.dominio.ProdutoRepositorio;
import com.joaoalcantara.pedidos.usuario.dominio.Usuario;
import com.joaoalcantara.pedidos.usuario.dominio.UsuarioRepositorio;

/**
 * A transacao que cria o pedido e reserva o estoque.
 *
 * <p>Esta classe existe separada de {@code PedidoServico} por um motivo tecnico,
 * nao estetico: quando o indice unico da chave de idempotencia e violado, a
 * transacao corrente fica marcada para rollback e o PostgreSQL recusa qualquer
 * comando seguinte nela ({@code current transaction is aborted}). Para ler o
 * pedido que a outra requisicao acabou de criar e preciso uma transacao
 * <b>nova</b> — e so ha transacao nova se a que falhou tiver terminado, ou seja,
 * se o tratamento do erro estiver fora dela.</p>
 *
 * <p>Tentar resolver isso com {@code try/catch} dentro de um unico metodo
 * {@code @Transactional} e uma armadilha classica: o catch executa, parece
 * funcionar em teste com H2, e falha no PostgreSQL de producao.</p>
 */
@Component
class CriadorDePedido {

    private final PedidoRepositorio pedidos;
    private final ProdutoRepositorio produtos;
    private final UsuarioRepositorio usuarios;
    private final Clock relogio;

    CriadorDePedido(PedidoRepositorio pedidos, ProdutoRepositorio produtos,
                    UsuarioRepositorio usuarios, Clock relogio) {
        this.pedidos = pedidos;
        this.produtos = produtos;
        this.usuarios = usuarios;
        this.relogio = relogio;
    }

    @Transactional
    Pedido criar(Long usuarioId, String chaveIdempotencia, PedidoRequisicao requisicao) {
        Usuario usuario = usuarios.porId(usuarioId)
                .orElseThrow(() -> RecursoNaoEncontradoException.de("Usuario", usuarioId));

        Pedido pedido = new Pedido(usuario, chaveIdempotencia, relogio.instant());

        for (ItemRequisicao item : emOrdemDeId(requisicao.itens())) {
            // porIdComTrava: a linha do produto fica travada ate o fim desta
            // transacao, entao duas reservas concorrentes do mesmo produto viram
            // uma fila em vez de uma disputa.
            Produto produto = produtos.porIdComTrava(item.produtoId())
                    .orElseThrow(() -> RecursoNaoEncontradoException.de("Produto", item.produtoId()));

            produto.exigirDisponivelParaVenda();
            produto.reservar(item.quantidade());
            produtos.salvar(produto);

            pedido.adicionarItem(produto, item.quantidade());
        }

        return pedidos.salvar(pedido);
    }

    /**
     * Ordena os itens por id de produto antes de travar.
     *
     * <p>Sem isso, um pedido de [A, B] e outro de [B, A] travariam os dois
     * produtos em ordens opostas e esperariam um pelo outro — um deadlock, que o
     * banco resolve matando uma das transacoes. Travando sempre na mesma ordem,
     * o ciclo nao chega a se formar.</p>
     */
    private List<ItemRequisicao> emOrdemDeId(List<ItemRequisicao> itens) {
        exigirProdutosDistintos(itens);
        return itens.stream().sorted(Comparator.comparing(ItemRequisicao::produtoId)).toList();
    }

    /**
     * Dois itens para o mesmo produto sao recusados, em vez de somados.
     *
     * <p>Somar seria conveniente, mas esconderia um erro de quem chamou: receber
     * de volta um pedido diferente do que se enviou e pior do que receber um
     * erro explicito.</p>
     */
    private void exigirProdutosDistintos(List<ItemRequisicao> itens) {
        long distintos = itens.stream().map(ItemRequisicao::produtoId).distinct().count();
        if (distintos != itens.size()) {
            throw new RegraDeNegocioException("item-duplicado",
                    "O mesmo produto aparece mais de uma vez; envie uma linha por produto");
        }
    }
}
