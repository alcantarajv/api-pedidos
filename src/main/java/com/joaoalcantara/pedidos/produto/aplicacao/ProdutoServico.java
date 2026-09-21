package com.joaoalcantara.pedidos.produto.aplicacao;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.joaoalcantara.pedidos.comum.erro.ConflitoException;
import com.joaoalcantara.pedidos.comum.erro.RecursoNaoEncontradoException;
import com.joaoalcantara.pedidos.produto.api.ProdutoRequisicao;
import com.joaoalcantara.pedidos.produto.dominio.Produto;
import com.joaoalcantara.pedidos.produto.dominio.ProdutoRepositorio;

/** Casos de uso administrativos sobre o catalogo. */
@Service
public class ProdutoServico {

    private final ProdutoRepositorio repositorio;

    public ProdutoServico(ProdutoRepositorio repositorio) {
        this.repositorio = repositorio;
    }

    @Transactional
    public Produto criar(ProdutoRequisicao requisicao) {
        if (repositorio.existeComNome(requisicao.nome())) {
            throw new ConflitoException("produto-duplicado",
                    "Ja existe um produto chamado '%s'".formatted(requisicao.nome()));
        }
        Produto produto = new Produto(requisicao.nome(), requisicao.descricao(),
                requisicao.preco(), requisicao.estoqueInicial());
        return repositorio.salvar(produto);
    }

    /**
     * Atualiza nome, descricao e preco. O estoque nao e tocado aqui.
     *
     * <p>Alterar o preco nao mexe em pedido nenhum: o valor de cada item fica
     * congelado no momento da compra. Sem isso, um reajuste no catalogo
     * reescreveria o historico de todo mundo.</p>
     */
    @Transactional
    public Produto atualizar(Long id, ProdutoRequisicao requisicao) {
        Produto produto = buscar(id);
        boolean trocouDeNome = !produto.getNome().equalsIgnoreCase(requisicao.nome());
        if (trocouDeNome && repositorio.existeComNome(requisicao.nome())) {
            throw new ConflitoException("produto-duplicado",
                    "Ja existe um produto chamado '%s'".formatted(requisicao.nome()));
        }
        produto.atualizarDados(requisicao.nome(), requisicao.descricao(), requisicao.preco());
        return repositorio.salvar(produto);
    }

    @Transactional
    public Produto ajustarEstoque(Long id, int estoqueDisponivel) {
        Produto produto = buscar(id);
        produto.ajustarEstoqueDisponivel(estoqueDisponivel);
        return repositorio.salvar(produto);
    }

    @Transactional
    public Produto alterarSituacao(Long id, boolean ativo) {
        Produto produto = buscar(id);
        if (ativo) {
            produto.ativar();
        } else {
            produto.desativar();
        }
        return repositorio.salvar(produto);
    }

    @Transactional(readOnly = true)
    public Produto buscar(Long id) {
        return repositorio.porId(id)
                .orElseThrow(() -> RecursoNaoEncontradoException.de("Produto", id));
    }

    @Transactional(readOnly = true)
    public List<Produto> listar(boolean apenasAtivos) {
        return repositorio.listar(apenasAtivos);
    }
}
