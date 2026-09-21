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

    List<Produto> listar(boolean apenasAtivos);

    boolean existeComNome(String nome);
}
