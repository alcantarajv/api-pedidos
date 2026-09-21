package com.joaoalcantara.pedidos.produto.api;

import java.math.BigDecimal;

import com.joaoalcantara.pedidos.produto.dominio.Produto;

public record ProdutoResposta(
        Long id,
        String nome,
        String descricao,
        BigDecimal preco,
        int estoqueDisponivel,
        int estoqueReservado,
        int estoqueTotal,
        boolean ativo) {

    public static ProdutoResposta de(Produto produto) {
        return new ProdutoResposta(
                produto.getId(),
                produto.getNome(),
                produto.getDescricao(),
                produto.getPreco(),
                produto.getEstoqueDisponivel(),
                produto.getEstoqueReservado(),
                produto.getEstoqueTotal(),
                produto.isAtivo());
    }
}
