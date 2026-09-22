package com.joaoalcantara.pedidos.outbox.dominio;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Um evento de dominio esperando para ser publicado na fila.
 *
 * <p>Esta linha e gravada <b>na mesma transacao</b> que produziu o fato que ela
 * descreve. E so isso que resolve o problema de escrever em dois sistemas que
 * nao compartilham transacao:</p>
 *
 * <ul>
 *   <li>Publicar antes de commitar: se o commit falha, foi publicado um evento
 *       sobre algo que nao aconteceu.</li>
 *   <li>Commitar antes de publicar: se a publicacao falha, o fato aconteceu e
 *       ninguem foi avisado.</li>
 * </ul>
 *
 * <p>Com o outbox, so existe uma escrita — no banco. Se ela falhar, nem o fato
 * nem o evento existem. Se ela commitar, o evento esta guardado e sera entregue
 * mais cedo ou mais tarde, por um processo separado.</p>
 *
 * <p>O preco: a entrega e <b>pelo menos uma vez</b>, nunca exatamente uma. Se o
 * worker publicar e morrer antes de marcar a linha, ele publica de novo na
 * proxima rodada. Por isso todo consumidor precisa ser idempotente.</p>
 */
@Entity
@Table(name = "outbox")
public class OutboxEvento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Identificador do evento, gerado por nos.
     *
     * <p>E ele que viaja na mensagem e serve de chave de idempotencia para quem
     * consome. Republicar a mesma linha produz a mesma chave — que e
     * exatamente o que permite ao consumidor reconhecer a repeticao.</p>
     */
    @Column(name = "id_evento", nullable = false, length = 36, unique = true)
    private String idEvento;

    @Column(name = "tipo", nullable = false, length = 80)
    private String tipo;

    /** A que agregado o evento se refere — aqui, o id do pedido. */
    @Column(name = "agregado_id", nullable = false, length = 60)
    private String agregadoId;

    @Column(name = "payload", nullable = false, columnDefinition = "text")
    private String payload;

    @Column(name = "criado_em", nullable = false)
    private Instant criadoEm;

    /** Nulo enquanto pendente; preenchido quando o broker confirma o recebimento. */
    @Column(name = "publicado_em")
    private Instant publicadoEm;

    @Column(name = "tentativas", nullable = false)
    private int tentativas;

    @Column(name = "ultimo_erro", length = 500)
    private String ultimoErro;

    /**
     * O id de correlacao de quem originou o evento.
     *
     * <p>Copiado para o cabecalho da mensagem na publicacao, e recolocado no MDC
     * pelo consumidor. E o que permite seguir um pedido do webhook ate a
     * notificacao com uma unica busca no log.</p>
     */
    @Column(name = "correlacao_id", length = 64)
    private String correlacaoId;

    protected OutboxEvento() {
        // exigido pelo JPA
    }

    public OutboxEvento(String tipo, String agregadoId, String payload, Instant criadoEm, String correlacaoId) {
        this.idEvento = UUID.randomUUID().toString();
        this.correlacaoId = correlacaoId;
        this.tipo = Objects.requireNonNull(tipo, "tipo");
        this.agregadoId = Objects.requireNonNull(agregadoId, "agregadoId");
        this.payload = Objects.requireNonNull(payload, "payload");
        this.criadoEm = Objects.requireNonNull(criadoEm, "criadoEm");
        this.tentativas = 0;
    }

    /**
     * Marca como entregue.
     *
     * <p>Chamado apenas depois que o broker <b>confirmou</b> o recebimento — nao
     * quando a chamada de publicacao retorna. Sem a confirmacao, "publiquei"
     * significa so "escrevi no socket", e marcar a linha aqui perderia o evento
     * silenciosamente se o broker caisse no meio.</p>
     */
    public void marcarPublicado(Instant agora) {
        this.publicadoEm = Objects.requireNonNull(agora, "agora");
        this.ultimoErro = null;
    }

    /** Registra a falha e mantem a linha pendente para a proxima rodada. */
    public void registrarFalha(String erro) {
        this.tentativas++;
        this.ultimoErro = erro == null ? null : erro.substring(0, Math.min(erro.length(), 500));
    }

    public boolean pendente() {
        return publicadoEm == null;
    }

    public Long getId() {
        return id;
    }

    public String getIdEvento() {
        return idEvento;
    }

    public String getTipo() {
        return tipo;
    }

    public String getAgregadoId() {
        return agregadoId;
    }

    public String getPayload() {
        return payload;
    }

    public Instant getCriadoEm() {
        return criadoEm;
    }

    public Instant getPublicadoEm() {
        return publicadoEm;
    }

    public int getTentativas() {
        return tentativas;
    }

    public String getUltimoErro() {
        return ultimoErro;
    }

    public String getCorrelacaoId() {
        return correlacaoId;
    }
}
