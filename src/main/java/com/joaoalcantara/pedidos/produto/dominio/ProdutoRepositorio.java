package com.joaoalcantara.pedidos.produto.dominio;

import java.util.List;
import java.util.Optional;

/**
 * Porta de persistencia do catalogo.
 *
 * <p>O dominio depende desta interface, nao do Spring Data. O adaptador vive em
 * {@code produto.infra}. Assim a camada de servico e testavel com um duble em
 * memoria, e a traducao de erros de persistencia — que na Etapa 4 vai incluir a
 * violacao da check constraint de estoque — tem um lugar natural para morar.</p>
 */
public interface ProdutoRepositorio {

    Produto salvar(Produto produto);

    Optional<Produto> porId(Long id);

    /**
     * Carrega o produto travando a linha ate o fim da transacao
     * ({@code SELECT ... FOR UPDATE}).
     *
     * <p>E o que torna a reserva de estoque segura sob concorrencia: entre ler
     * "ha 1 unidade" e gravar "agora ha 0" existe uma janela, e sem o lock duas
     * transacoes passam por ela ao mesmo tempo e vendem a mesma unidade duas
     * vezes. Com ele, a segunda transacao espera a primeira terminar e enxerga o
     * estoque ja decrementado.</p>
     *
     * <p>O lock serializa apenas as reservas <b>daquele produto</b>. Pedidos de
     * produtos diferentes nao se esperam.</p>
     */
    Optional<Produto> porIdComTrava(Long id);

    List<Produto> listar(boolean apenasAtivos);

    boolean existeComNome(String nome);
}
