package com.joaoalcantara.pedidos.consumo.dominio;

import java.time.Instant;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Um aviso enviado ao cliente.
 *
 * <p>Neste projeto o "envio" e a propria gravacao desta linha: nao ha servidor
 * de e-mail, e simular um traria complexidade sem ensinar nada novo. A tabela
 * cumpre o papel que importa aqui — ser um efeito colateral <b>observavel</b>,
 * que um teste consegue contar para provar que a entrega duplicada nao gerou
 * dois avisos.</p>
 *
 * <p>Num sistema real, esta seria a fronteira com o provedor de e-mail. E vale
 * notar: e a pior fronteira possivel para nao ser idempotente, porque e-mail
 * enviado nao tem rollback.</p>
 */
@Entity
@Table(name = "notificacoes")
public class Notificacao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "pedido_id", nullable = false)
    private Long pedidoId;

    @Column(name = "usuario_id", nullable = false)
    private Long usuarioId;

    @Column(name = "tipo", nullable = false, length = 60)
    private String tipo;

    @Column(name = "mensagem", nullable = false, length = 300)
    private String mensagem;

    @Column(name = "criada_em", nullable = false)
    private Instant criadaEm;

    protected Notificacao() {
        // exigido pelo JPA
    }

    public Notificacao(Long pedidoId, Long usuarioId, String tipo, String mensagem, Instant criadaEm) {
        this.pedidoId = Objects.requireNonNull(pedidoId, "pedidoId");
        this.usuarioId = Objects.requireNonNull(usuarioId, "usuarioId");
        this.tipo = Objects.requireNonNull(tipo, "tipo");
        this.mensagem = Objects.requireNonNull(mensagem, "mensagem");
        this.criadaEm = Objects.requireNonNull(criadaEm, "criadaEm");
    }

    public Long getId() {
        return id;
    }

    public Long getPedidoId() {
        return pedidoId;
    }

    public Long getUsuarioId() {
        return usuarioId;
    }

    public String getTipo() {
        return tipo;
    }

    public String getMensagem() {
        return mensagem;
    }

    public Instant getCriadaEm() {
        return criadaEm;
    }
}
