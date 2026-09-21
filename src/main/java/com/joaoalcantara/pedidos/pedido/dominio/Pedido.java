package com.joaoalcantara.pedidos.pedido.dominio;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import com.joaoalcantara.pedidos.produto.dominio.Produto;
import com.joaoalcantara.pedidos.usuario.dominio.Usuario;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

/**
 * Pedido de um cliente: raiz do agregado que carrega seus itens.
 *
 * <p>Itens nao existem fora de um pedido e mudam junto com ele, por isso o
 * {@code cascade} e o {@code orphanRemoval}: quem manipula item passa pelo
 * pedido.</p>
 *
 * <p>O {@code valorTotal} e um campo gravado, nao um calculo feito na leitura.
 * Some-se os itens a cada exibicao e o total passaria a depender de como o
 * codigo esta hoje; gravado, ele registra o que foi combinado com o cliente
 * naquele instante — que e o que importa quando alguem contesta a cobranca.</p>
 */
@Entity
@Table(name = "pedidos")
public class Pedido {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private StatusPedido status;

    @Column(name = "valor_total", nullable = false, precision = 12, scale = 2)
    private BigDecimal valorTotal;

    /**
     * Chave enviada pelo cliente no cabecalho {@code Idempotency-Key}.
     *
     * <p>Unica por usuario — nao globalmente. Se fosse global, mandar a chave de
     * outra pessoa devolveria o pedido dela.</p>
     */
    @Column(name = "chave_idempotencia", nullable = false, length = 80)
    private String chaveIdempotencia;

    @Column(name = "criado_em", nullable = false)
    private Instant criadoEm;

    @OneToMany(mappedBy = "pedido", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ItemPedido> itens = new ArrayList<>();

    protected Pedido() {
        // exigido pelo JPA
    }

    public Pedido(Usuario usuario, String chaveIdempotencia, Instant criadoEm) {
        this.usuario = Objects.requireNonNull(usuario, "usuario");
        this.chaveIdempotencia = Objects.requireNonNull(chaveIdempotencia, "chaveIdempotencia");
        this.criadoEm = Objects.requireNonNull(criadoEm, "criadoEm");
        this.status = StatusPedido.AGUARDANDO_PAGAMENTO;
        this.valorTotal = BigDecimal.ZERO.setScale(2, RoundingMode.UNNECESSARY);
    }

    /**
     * Acrescenta uma linha ao pedido, fotografando nome e preco do produto.
     *
     * <p>Nao mexe em estoque: reservar unidades envolve travar a linha do produto
     * no banco, e isso e trabalho do servico, que tem transacao. A entidade
     * cuida do que e o pedido; o servico, de como ele conversa com o resto.</p>
     */
    public ItemPedido adicionarItem(Produto produto, int quantidade) {
        ItemPedido item = new ItemPedido(this, produto, quantidade);
        itens.add(item);
        recalcularTotal();
        return item;
    }

    private void recalcularTotal() {
        this.valorTotal = itens.stream()
                .map(ItemPedido::getSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
    }

    /** O pedido pertence a este usuario? Base da regra "cliente so ve os proprios". */
    public boolean pertenceA(Long usuarioId) {
        return usuario.getId().equals(usuarioId);
    }

    public Long getId() {
        return id;
    }

    public Usuario getUsuario() {
        return usuario;
    }

    public Long getUsuarioId() {
        return usuario.getId();
    }

    public StatusPedido getStatus() {
        return status;
    }

    public BigDecimal getValorTotal() {
        return valorTotal;
    }

    public String getChaveIdempotencia() {
        return chaveIdempotencia;
    }

    public Instant getCriadoEm() {
        return criadoEm;
    }

    public List<ItemPedido> getItens() {
        return Collections.unmodifiableList(itens);
    }
}
