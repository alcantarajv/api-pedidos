package com.joaoalcantara.pedidos.webhook.dominio;

import java.time.Instant;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Registro de que um evento do gateway ja foi consumido.
 *
 * <p>Esta tabela nao guarda regra de negocio nenhuma — ela existe so para ter
 * uma restricao de unicidade sobre o identificador do evento. E essa restricao,
 * gravada <b>na mesma transacao</b> que aplica o efeito, que torna o
 * processamento idempotente:</p>
 *
 * <pre>
 * BEGIN
 *   INSERT INTO eventos_processados (id_externo) VALUES (?)   -- colide na 2a vez
 *   ... aplica o efeito: pedido PAGO, estoque baixado ...
 * COMMIT
 * </pre>
 *
 * <p>Ou as duas coisas acontecem, ou nenhuma. Nao ha como o efeito ser aplicado
 * sem o registro, nem o registro existir sem o efeito — que e exatamente a
 * garantia que uma consulta antes do processamento nao consegue dar.</p>
 */
@Entity
@Table(name = "eventos_processados")
public class EventoProcessado {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** O identificador que o gateway da ao evento (evt_... no Stripe). */
    @Column(name = "id_externo", nullable = false, length = 120)
    private String idExterno;

    @Column(name = "tipo", nullable = false, length = 80)
    private String tipo;

    @Column(name = "processado_em", nullable = false)
    private Instant processadoEm;

    protected EventoProcessado() {
        // exigido pelo JPA
    }

    public EventoProcessado(String idExterno, String tipo, Instant processadoEm) {
        this.idExterno = Objects.requireNonNull(idExterno, "idExterno");
        this.tipo = Objects.requireNonNull(tipo, "tipo");
        this.processadoEm = Objects.requireNonNull(processadoEm, "processadoEm");
    }

    public Long getId() {
        return id;
    }

    public String getIdExterno() {
        return idExterno;
    }

    public String getTipo() {
        return tipo;
    }

    public Instant getProcessadoEm() {
        return processadoEm;
    }
}
