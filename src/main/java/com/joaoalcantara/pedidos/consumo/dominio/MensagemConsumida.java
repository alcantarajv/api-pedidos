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
 * Registro de que <b>um consumidor</b> ja tratou <b>uma mensagem</b>.
 *
 * <p>A chave e o par (mensagem, consumidor), nao so a mensagem. O mesmo evento
 * {@code pedido.pago} e entregue ao consumidor de estoque e ao de notificacao:
 * se a chave fosse so o id da mensagem, o primeiro a processar bloquearia o
 * segundo, e o cliente nunca receberia o aviso.</p>
 *
 * <p>Vale o mesmo raciocinio da tabela de eventos do gateway: o registro e
 * gravado na MESMA transacao do efeito. A diferenca e o lado da conversa —
 * aquela protege a entrada vinda do gateway, esta protege o consumo da fila,
 * onde a entrega tambem e "pelo menos uma vez".</p>
 */
@Entity
@Table(name = "mensagens_consumidas")
public class MensagemConsumida {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "id_mensagem", nullable = false, length = 80)
    private String idMensagem;

    @Column(name = "consumidor", nullable = false, length = 80)
    private String consumidor;

    @Column(name = "consumida_em", nullable = false)
    private Instant consumidaEm;

    protected MensagemConsumida() {
        // exigido pelo JPA
    }

    public MensagemConsumida(String idMensagem, String consumidor, Instant consumidaEm) {
        this.idMensagem = Objects.requireNonNull(idMensagem, "idMensagem");
        this.consumidor = Objects.requireNonNull(consumidor, "consumidor");
        this.consumidaEm = Objects.requireNonNull(consumidaEm, "consumidaEm");
    }

    public Long getId() {
        return id;
    }

    public String getIdMensagem() {
        return idMensagem;
    }

    public String getConsumidor() {
        return consumidor;
    }

    public Instant getConsumidaEm() {
        return consumidaEm;
    }
}
