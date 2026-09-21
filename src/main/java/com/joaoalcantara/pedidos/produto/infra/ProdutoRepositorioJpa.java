package com.joaoalcantara.pedidos.produto.infra;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Repository;

import com.joaoalcantara.pedidos.produto.dominio.Produto;
import com.joaoalcantara.pedidos.produto.dominio.ProdutoRepositorio;

/** Adaptador que liga a porta do dominio ao Spring Data. */
@Repository
class ProdutoRepositorioJpa implements ProdutoRepositorio {

    private final ProdutoSpringDataRepository jpa;

    ProdutoRepositorioJpa(ProdutoSpringDataRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Produto salvar(Produto produto) {
        return jpa.save(produto);
    }

    @Override
    public Optional<Produto> porId(Long id) {
        return jpa.findById(id);
    }

    @Override
    public List<Produto> listar(boolean apenasAtivos) {
        return apenasAtivos ? jpa.findByAtivoTrueOrderByNomeAsc() : jpa.findAllByOrderByNomeAsc();
    }

    @Override
    public boolean existeComNome(String nome) {
        return jpa.existsByNomeIgnoreCase(nome);
    }
}
