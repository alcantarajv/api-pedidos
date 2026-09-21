package com.joaoalcantara.pedidos.pedido.dominio;

import java.math.BigDecimal;
import java.util.Objects;

import com.joaoalcantara.pedidos.produto.dominio.Produto;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * Uma linha do pedido, com o produto <b>fotografado</b> no momento da compra.
 *
 * <p>O preco e o nome sao copiados para ca, em vez de lidos do {@link Produto} na
 * hora de exibir. Sem essa copia, um reajuste no catalogo reescreveria o
 * historico de todo mundo: o cliente abriria um pedido de marco e veria o preco
 * de agosto, e a soma dos itens deixaria de bater com o valor cobrado.</p>
 *
 * <p>A referencia ao produto continua existindo — e ela que permite baixar o
 * estoque certo —, mas ela responde "o que foi comprado", nao "quanto custa".</p>
 */
@Entity
@Table(name = "itens_pedido")
public class ItemPedido {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pedido_id", nullable = false)
    private Pedido pedido;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "produto_id", nullable = false)
    private Produto produto;

    @Column(name = "nome_produto", nullable = false, length = 140)
    private String nomeProduto;

    @Column(name = "quantidade", nullable = false)
    private int quantidade;

    @Column(name = "preco_unitario", nullable = false, precision = 12, scale = 2)
    private BigDecimal precoUnitario;

    protected ItemPedido() {
        // exigido pelo JPA
    }

    ItemPedido(Pedido pedido, Produto produto, int quantidade) {
        this.pedido = Objects.requireNonNull(pedido, "pedido");
        this.produto = Objects.requireNonNull(produto, "produto");
        if (quantidade <= 0) {
            throw new IllegalArgumentException("A quantidade deve ser positiva, recebida: " + quantidade);
        }
        this.quantidade = quantidade;
        // A fotografia acontece aqui, uma unica vez.
        this.nomeProduto = produto.getNome();
        this.precoUnitario = produto.getPreco();
    }

    public BigDecimal getSubtotal() {
        return precoUnitario.multiply(BigDecimal.valueOf(quantidade));
    }

    public Long getId() {
        return id;
    }

    public Pedido getPedido() {
        return pedido;
    }

    public Produto getProduto() {
        return produto;
    }

    public Long getProdutoId() {
        return produto.getId();
    }

    public String getNomeProduto() {
        return nomeProduto;
    }

    public int getQuantidade() {
        return quantidade;
    }

    public BigDecimal getPrecoUnitario() {
        return precoUnitario;
    }
}
