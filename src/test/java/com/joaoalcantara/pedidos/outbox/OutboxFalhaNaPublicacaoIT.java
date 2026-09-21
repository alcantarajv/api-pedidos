package com.joaoalcantara.pedidos.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.joaoalcantara.pedidos.comum.TesteDeIntegracao;
import com.joaoalcantara.pedidos.outbox.aplicacao.RegistradorDeEvento;
import com.joaoalcantara.pedidos.outbox.aplicacao.WorkerDoOutbox;
import com.joaoalcantara.pedidos.outbox.dominio.EventosDePedido;
import com.joaoalcantara.pedidos.outbox.dominio.FalhaNaPublicacaoException;
import com.joaoalcantara.pedidos.outbox.dominio.OutboxEvento;
import com.joaoalcantara.pedidos.outbox.dominio.OutboxRepositorio;
import com.joaoalcantara.pedidos.outbox.dominio.PedidoPago;
import com.joaoalcantara.pedidos.outbox.dominio.PublicadorDeMensagem;

/**
 * O caminho que mais importa no outbox: o broker fora do ar.
 *
 * <p>E o cenario que justifica o padrao inteiro. Se a publicacao falhasse e o
 * evento sumisse, o outbox nao serviria para nada — seria uma tabela a mais
 * entre a aplicacao e a fila.</p>
 *
 * <p>O publicador e substituido por um dublê que falha porque derrubar o broker
 * de verdade no meio do teste seria lento, instavel e dificil de repetir. O que
 * precisa ser verificado aqui nao e o RabbitMQ — e o que <b>o worker faz</b>
 * quando a publicacao nao dá certo.</p>
 */
@TesteDeIntegracao
class OutboxFalhaNaPublicacaoIT {

    @MockitoBean
    private PublicadorDeMensagem publicador;

    @Autowired
    private WorkerDoOutbox worker;

    @Autowired
    private RegistradorDeEvento registrador;

    @Autowired
    private OutboxRepositorio outbox;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void limpar() {
        jdbc.execute("TRUNCATE TABLE outbox RESTART IDENTITY");
    }

    private OutboxEvento registrarPedidoPago(long pedidoId) {
        return registrador.registrar(EventosDePedido.PEDIDO_PAGO, String.valueOf(pedidoId),
                new PedidoPago(pedidoId, 1L, new BigDecimal("300.00"), "pi_" + UUID.randomUUID(), Instant.now()));
    }

    @Test
    @DisplayName("falha na publicacao mantem o evento pendente, com a tentativa registrada")
    void falhaMantemPendente() {
        doThrow(new FalhaNaPublicacaoException("broker fora do ar"))
                .when(publicador).publicar(any(OutboxEvento.class));

        OutboxEvento evento = registrarPedidoPago(42);

        int entregues = worker.despacharLote();

        assertThat(entregues).isZero();
        assertThat(outbox.contarPendentes())
                .as("o evento continua no outbox esperando nova tentativa")
                .isEqualTo(1);

        assertThat(jdbc.queryForObject("SELECT publicado_em FROM outbox WHERE id_evento = ?",
                java.sql.Timestamp.class, evento.getIdEvento()))
                .as("nao pode ser marcado como publicado")
                .isNull();
        assertThat(jdbc.queryForObject("SELECT tentativas FROM outbox WHERE id_evento = ?",
                Integer.class, evento.getIdEvento()))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT ultimo_erro FROM outbox WHERE id_evento = ?",
                String.class, evento.getIdEvento()))
                .as("o motivo fica registrado para quem for investigar")
                .contains("broker fora do ar");
    }

    @Test
    @DisplayName("tentativas sucessivas se acumulam, e a entrega acontece quando o broker volta")
    void entregaQuandoOBrokerVolta() {
        doThrow(new FalhaNaPublicacaoException("broker fora do ar"))
                .when(publicador).publicar(any(OutboxEvento.class));

        OutboxEvento evento = registrarPedidoPago(7);

        worker.despacharLote();
        worker.despacharLote();
        worker.despacharLote();

        assertThat(jdbc.queryForObject("SELECT tentativas FROM outbox WHERE id_evento = ?",
                Integer.class, evento.getIdEvento()))
                .isEqualTo(3);

        // O broker volta.
        doNothing().when(publicador).publicar(any(OutboxEvento.class));

        assertThat(worker.despacharLote()).isEqualTo(1);
        assertThat(outbox.contarPendentes())
                .as("nenhum evento se perdeu durante a indisponibilidade")
                .isZero();
    }

    @Test
    @DisplayName("um evento que falha nao impede a entrega dos outros do lote")
    void falhaNaoInterrompeOLote() {
        OutboxEvento problematico = registrarPedidoPago(1);
        registrarPedidoPago(2);
        registrarPedidoPago(3);

        doThrow(new FalhaNaPublicacaoException("routing key sem fila"))
                .when(publicador).publicar(org.mockito.ArgumentMatchers.argThat(
                        evento -> evento != null && evento.getIdEvento().equals(problematico.getIdEvento())));

        int entregues = worker.despacharLote();

        assertThat(entregues).as("os outros dois passaram").isEqualTo(2);
        assertThat(outbox.contarPendentes()).as("so o problematico ficou").isEqualTo(1);
    }
}
