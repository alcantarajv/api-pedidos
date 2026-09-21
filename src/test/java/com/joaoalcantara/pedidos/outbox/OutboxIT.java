package com.joaoalcantara.pedidos.outbox;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.joaoalcantara.pedidos.comum.FilaDeTeste;
import com.joaoalcantara.pedidos.comum.TesteDeIntegracao;
import com.joaoalcantara.pedidos.outbox.aplicacao.RegistradorDeEvento;
import com.joaoalcantara.pedidos.outbox.aplicacao.WorkerDoOutbox;
import com.joaoalcantara.pedidos.outbox.dominio.EventosDePedido;
import com.joaoalcantara.pedidos.outbox.dominio.OutboxEvento;
import com.joaoalcantara.pedidos.outbox.dominio.OutboxRepositorio;
import com.joaoalcantara.pedidos.outbox.dominio.PedidoPago;
import com.joaoalcantara.pedidos.pedido.aplicacao.PedidoServico;
import com.joaoalcantara.pedidos.pedido.api.ItemRequisicao;
import com.joaoalcantara.pedidos.pedido.api.PedidoRequisicao;
import com.joaoalcantara.pedidos.produto.dominio.Produto;
import com.joaoalcantara.pedidos.produto.dominio.ProdutoRepositorio;
import com.joaoalcantara.pedidos.seguranca.UsuarioAutenticado;
import com.joaoalcantara.pedidos.usuario.dominio.Papel;
import com.joaoalcantara.pedidos.usuario.dominio.Usuario;
import com.joaoalcantara.pedidos.usuario.dominio.UsuarioRepositorio;

/**
 * O worker do outbox contra o RabbitMQ de verdade.
 *
 * <p>Os testes chamam {@code despacharLote()} diretamente em vez de esperar o
 * agendador: teste que depende de {@code sleep} e lento e instavel.</p>
 */
@TesteDeIntegracao
class OutboxIT {

    @Autowired
    private WorkerDoOutbox worker;

    @Autowired
    private RegistradorDeEvento registrador;

    @Autowired
    private OutboxRepositorio outbox;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PedidoServico pedidoServico;

    @Autowired
    private ProdutoRepositorio produtos;

    @Autowired
    private UsuarioRepositorio usuarios;

    private Long usuarioRealId;

    @BeforeEach
    void limpar() {
        jdbc.execute("TRUNCATE TABLE outbox RESTART IDENTITY");
        esvaziarFila(FilaDeTeste.FILA);
    }

    /**
     * Cada evento ganha um pedido proprio, de verdade.
     *
     * <p>Os consumidores da Etapa 9 estao ativos neste mesmo contexto e escutam
     * as filas reais. Eventos apontando para um pedido inventado — ou varios
     * eventos para o mesmo pedido, que so tem estoque reservado uma vez — fariam
     * o consumidor falhar, gastar o ciclo de retentativas e atrasar a fila para
     * os outros testes.</p>
     */
    private Long criarPedidoReal() {
        Usuario cliente = usuarios.salvar(new Usuario("Cliente", "cliente-" + UUID.randomUUID() + "@exemplo.com",
                "$2a$10$hashfalsoparateste000000000000000000000000000000000000", Papel.CLIENTE, Instant.now()));
        usuarioRealId = cliente.getId();
        Produto produto = produtos.salvar(
                new Produto("Produto " + UUID.randomUUID(), "d", new BigDecimal("100.00"), 500));

        var resultado = pedidoServico.criar(
                new UsuarioAutenticado(cliente.getId(), cliente.getNome(), cliente.getPapel()),
                UUID.randomUUID().toString(),
                new PedidoRequisicao(List.of(new ItemRequisicao(produto.getId(), 1))));
        return resultado.pedido().getId();
    }

    private void esvaziarFila(String fila) {
        while (rabbitTemplate.receive(fila, 50) != null) {
            // descarta o que tiver sobrado de outro teste
        }
    }

    private OutboxEvento registrarPedidoPago() {
        Long pedidoId = criarPedidoReal();
        return registrador.registrar(EventosDePedido.PEDIDO_PAGO, String.valueOf(pedidoId),
                new PedidoPago(pedidoId, usuarioRealId, new BigDecimal("300.00"), "pi_" + UUID.randomUUID(), Instant.now()));
    }

    @Test
    @DisplayName("o evento sai do outbox, chega na fila e a linha e marcada como publicada")
    void entregaOEventoPendente() {
        OutboxEvento evento = registrarPedidoPago();
        assertThat(evento.pendente()).isTrue();

        int entregues = worker.despacharLote();

        assertThat(entregues).isEqualTo(1);

        Message mensagem = rabbitTemplate.receive(FilaDeTeste.FILA, 2000);
        assertThat(mensagem).as("a mensagem chegou na fila").isNotNull();
        assertThat(new String(mensagem.getBody())).contains("\"pedidoId\":");
        assertThat(mensagem.getMessageProperties().getHeaders())
                .as("o id do evento viaja na mensagem: e a chave de idempotencia do consumidor")
                .containsEntry("x-id-evento", evento.getIdEvento());

        assertThat(jdbc.queryForObject("SELECT publicado_em FROM outbox WHERE id_evento = ?",
                java.sql.Timestamp.class, evento.getIdEvento()))
                .as("so e marcado apos a confirmacao do broker")
                .isNotNull();
    }

    @Test
    @DisplayName("uma publicacao chega a todas as filas ligadas ao evento")
    void entregaParaTodosOsInteressados() {
        OutboxEvento evento = registrarPedidoPago();

        worker.despacharLote();

        // A fila de teste recebeu a sua copia...
        assertThat(rabbitTemplate.receive(FilaDeTeste.FILA, 2000)).isNotNull();

        // ...e os dois consumidores de producao receberam as deles. Uma
        // publicacao, tres filas: o publicador nao sabe quem escuta, e o topic
        // exchange resolve.
        Instant limite = Instant.now().plusSeconds(10);
        while (Instant.now().isBefore(limite) && consumosDe(evento.getIdEvento()) < 2) {
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
        }
        assertThat(consumosDe(evento.getIdEvento())).isEqualTo(2);
    }

    private int consumosDe(String idEvento) {
        Integer total = jdbc.queryForObject(
                "SELECT count(*) FROM mensagens_consumidas WHERE id_mensagem = ?", Integer.class, idEvento);
        return total == null ? 0 : total;
    }

    @Test
    @DisplayName("um lote entrega varios eventos e esvazia a fila de pendentes")
    void entregaLote() {
        for (int i = 1; i <= 5; i++) {
            registrarPedidoPago();
        }

        int entregues = worker.despacharLote();

        assertThat(entregues).isEqualTo(5);
        assertThat(outbox.contarPendentes()).isZero();
    }

    @Test
    @DisplayName("rodar de novo nao republica o que ja foi entregue")
    void naoRepublica() {
        registrarPedidoPago();
        worker.despacharLote();
        assertThat(rabbitTemplate.receive(FilaDeTeste.FILA, 2000)).isNotNull();

        int entreguesNaSegundaRodada = worker.despacharLote();

        assertThat(entreguesNaSegundaRodada).isZero();
        assertThat(rabbitTemplate.receive(FilaDeTeste.FILA, 300))
                .as("nenhuma mensagem nova")
                .isNull();
    }

    @Test
    @DisplayName("evento sem fila de destino nao e marcado como publicado")
    void eventoSemFilaDeDestino() {
        // Tipo sem binding nenhum: o exchange aceita, mas nenhuma fila quer.
        // Com mandatory=true a mensagem volta, e o worker trata como falha.
        // Sem isso, o broker a descartaria em silencio e a linha seria marcada
        // como entregue — perdendo o evento e escondendo o erro de configuracao.
        registrador.registrar("pedido.evento.sem.binding", "99", List.of());

        int entregues = worker.despacharLote();

        assertThat(entregues).isZero();
        assertThat(outbox.contarPendentes()).isEqualTo(1);

        Integer tentativas = jdbc.queryForObject(
                "SELECT tentativas FROM outbox WHERE tipo = 'pedido.evento.sem.binding'", Integer.class);
        assertThat(tentativas).isEqualTo(1);
    }
}
