package com.joaoalcantara.pedidos.manutencao;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import com.joaoalcantara.pedidos.comum.RelogioAjustavel;
import com.joaoalcantara.pedidos.comum.TesteDeIntegracao;
import com.joaoalcantara.pedidos.consumo.dominio.MensagemConsumida;
import com.joaoalcantara.pedidos.consumo.dominio.MensagemConsumidaRepositorio;
import com.joaoalcantara.pedidos.webhook.dominio.EventoProcessado;
import com.joaoalcantara.pedidos.webhook.dominio.EventoProcessadoRepositorio;

/**
 * A limpeza dos registros de idempotencia.
 *
 * <p>O que importa aqui e o limite: apagar o que passou da retencao e
 * <b>nao encostar</b> no que ainda esta dentro dela. Apagar cedo demais faria um
 * evento antigo ser reprocessado como novo, e a protecao inteira viraria pó.</p>
 */
@TesteDeIntegracao
@Import(RelogioAjustavel.class)
class LimpezaDeRegistrosIT {

    @Autowired
    private LimpezaDeRegistros limpeza;

    @Autowired
    private EventoProcessadoRepositorio eventos;

    @Autowired
    private MensagemConsumidaRepositorio mensagens;

    @Autowired
    private java.time.Clock relogio;

    @Autowired
    private JdbcTemplate jdbc;

    private RelogioAjustavel.ClockMovel relogioMovel() {
        return (RelogioAjustavel.ClockMovel) relogio;
    }

    @Test
    @DisplayName("remove o que passou da retencao e preserva o que ainda vale")
    void apagaSoOQuePassouDaRetencao() {
        Instant agora = relogio.instant();

        String antigo = "evt_antigo_" + UUID.randomUUID();
        String recente = "evt_recente_" + UUID.randomUUID();
        eventos.salvar(new EventoProcessado(antigo, "payment_intent.succeeded", agora.minus(Duration.ofDays(40))));
        eventos.salvar(new EventoProcessado(recente, "payment_intent.succeeded", agora.minus(Duration.ofDays(5))));

        String mensagemAntiga = "msg_antiga_" + UUID.randomUUID();
        String mensagemRecente = "msg_recente_" + UUID.randomUUID();
        mensagens.salvar(new MensagemConsumida(mensagemAntiga, "estoque", agora.minus(Duration.ofDays(40))));
        mensagens.salvar(new MensagemConsumida(mensagemRecente, "estoque", agora.minus(Duration.ofDays(5))));

        limpeza.limpar();

        assertThat(eventos.jaProcessado(antigo)).as("passou dos 30 dias: sai").isFalse();
        assertThat(eventos.jaProcessado(recente)).as("ainda dentro da retencao: fica").isTrue();
        assertThat(mensagens.jaConsumida(mensagemAntiga, "estoque")).isFalse();
        assertThat(mensagens.jaConsumida(mensagemRecente, "estoque")).isTrue();
    }

    @Test
    @DisplayName("evento do outbox ainda pendente nunca e apagado, por mais antigo que seja")
    void naoApagaPendenteDoOutbox() {
        // Uma linha antiga e NAO publicada: sinal de que a entrega vem falhando
        // ha muito tempo. Apagar seria destruir a prova de um problema — e o
        // proprio evento, que ninguem recebeu.
        jdbc.update("""
                INSERT INTO outbox (id_evento, tipo, agregado_id, payload, criado_em, tentativas)
                VALUES (?, 'pedido.pago', '999', '{}', ?, 12)
                """, UUID.randomUUID().toString(),
                java.sql.Timestamp.from(relogio.instant().minus(Duration.ofDays(60))));

        long pendentesAntes = jdbc.queryForObject(
                "SELECT count(*) FROM outbox WHERE publicado_em IS NULL", Long.class);

        relogioMovel().avancar(Duration.ofDays(1));
        limpeza.limpar();

        long pendentesDepois = jdbc.queryForObject(
                "SELECT count(*) FROM outbox WHERE publicado_em IS NULL", Long.class);
        assertThat(pendentesDepois).isEqualTo(pendentesAntes);
    }

    @Test
    @DisplayName("linha do outbox ja publicada e antiga e removida")
    void apagaPublicadoAntigo() {
        String idEvento = UUID.randomUUID().toString();
        jdbc.update("""
                INSERT INTO outbox (id_evento, tipo, agregado_id, payload, criado_em, publicado_em, tentativas)
                VALUES (?, 'pedido.pago', '999', '{}', ?, ?, 0)
                """, idEvento,
                java.sql.Timestamp.from(relogio.instant().minus(Duration.ofDays(60))),
                java.sql.Timestamp.from(relogio.instant().minus(Duration.ofDays(60))));

        limpeza.limpar();

        Integer restantes = jdbc.queryForObject(
                "SELECT count(*) FROM outbox WHERE id_evento = ?", Integer.class, idEvento);
        assertThat(restantes).isZero();
    }
}
