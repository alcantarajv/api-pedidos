package com.joaoalcantara.pedidos.pagamento.dominio;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

import com.joaoalcantara.pedidos.pedido.dominio.Pedido;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

/**
 * A cobranca de um pedido no gateway externo.
 *
 * <p>Existe como entidade propria, e nao como colunas dentro de {@code pedidos},
 * porque ela e o unico registro local do que aconteceu do lado de la: o
 * identificador externo, o valor enviado e o ultimo status conhecido. Quando o
 * gateway e o sistema discordarem — e um dia discordam —, esta tabela e o ponto
 * de partida da investigacao.</p>
 *
 * <p>O valor e copiado do pedido no momento da cobranca. E a mesma ideia do
 * preco congelado no item: o que foi cobrado nao muda porque o pedido mudou.</p>
 */
@Entity
@Table(name = "pagamentos")
public class Pagamento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pedido_id", nullable = false, unique = true)
    private Pedido pedido;

    /** Identificador da cobranca no gateway. Chave para reconciliar os dois lados. */
    @Column(name = "id_externo", nullable = false, length = 120)
    private String idExterno;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private StatusPagamento status;

    @Column(name = "valor", nullable = false, precision = 12, scale = 2)
    private BigDecimal valor;

    @Column(name = "criado_em", nullable = false)
    private Instant criadoEm;

    @Column(name = "atualizado_em", nullable = false)
    private Instant atualizadoEm;

    protected Pagamento() {
        // exigido pelo JPA
    }

    public Pagamento(Pedido pedido, String idExterno, BigDecimal valor, StatusPagamento status, Instant agora) {
        this.pedido = Objects.requireNonNull(pedido, "pedido");
        this.idExterno = Objects.requireNonNull(idExterno, "idExterno");
        this.valor = Objects.requireNonNull(valor, "valor");
        this.status = Objects.requireNonNull(status, "status");
        this.criadoEm = Objects.requireNonNull(agora, "agora");
        this.atualizadoEm = agora;
    }

    /**
     * Registra o status lido do gateway.
     *
     * <p>Nao ha maquina de estados aqui, e isso e deliberado: quem manda no
     * ciclo de vida da cobranca e o gateway, nao este sistema. Inventar um grafo
     * local so criaria divergencia quando o Stripe fizesse uma transicao que
     * nosso grafo nao previu.</p>
     */
    public void registrarStatus(StatusPagamento novo, Instant agora) {
        this.status = Objects.requireNonNull(novo, "novo");
        this.atualizadoEm = Objects.requireNonNull(agora, "agora");
    }

    public Long getId() {
        return id;
    }

    public Pedido getPedido() {
        return pedido;
    }

    public Long getPedidoId() {
        return pedido.getId();
    }

    public String getIdExterno() {
        return idExterno;
    }

    public StatusPagamento getStatus() {
        return status;
    }

    public BigDecimal getValor() {
        return valor;
    }

    public Instant getCriadoEm() {
        return criadoEm;
    }

    public Instant getAtualizadoEm() {
        return atualizadoEm;
    }
}
