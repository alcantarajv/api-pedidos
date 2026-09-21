package com.joaoalcantara.pedidos.produto.api;

import java.math.BigDecimal;

import com.joaoalcantara.pedidos.produto.dominio.Produto;

/** Visao administrativa: acrescenta as parcelas internas do estoque. */
public record ProdutoAdminResposta(
        Long id,
        String nome,
        String descricao,
        BigDecimal preco,
        int estoqueDisponivel,
        int estoqueReservado,
        int estoqueTotal,
        boolean ativo) implements ProdutoVisao {

    public static ProdutoAdminResposta de(Produto produto) {
        return new ProdutoAdminResposta(
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
