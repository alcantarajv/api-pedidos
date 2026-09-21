package com.joaoalcantara.pedidos.consumo;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.function.BooleanSupplier;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.joaoalcantara.pedidos.comum.TesteDeIntegracao;
import com.joaoalcantara.pedidos.consumo.dominio.NotificacaoRepositorio;
import com.joaoalcantara.pedidos.outbox.dominio.EventosDePedido;
import com.joaoalcantara.pedidos.outbox.infra.FilaConfig;
import com.joaoalcantara.pedidos.pedido.aplicacao.PedidoServico;
import com.joaoalcantara.pedidos.pedido.api.ItemRequisicao;
import com.joaoalcantara.pedidos.pedido.api.PedidoRequisicao;
import com.joaoalcantara.pedidos.pedido.dominio.Pedido;
import com.joaoalcantara.pedidos.produto.dominio.Produto;
import com.joaoalcantara.pedidos.produto.dominio.ProdutoRepositorio;
import com.joaoalcantara.pedidos.seguranca.UsuarioAutenticado;
import com.joaoalcantara.pedidos.usuario.dominio.Papel;
import com.joaoalcantara.pedidos.usuario.dominio.Usuario;
import com.joaoalcantara.pedidos.usuario.dominio.UsuarioRepositorio;

/**
 * Os consumidores da fila, com o RabbitMQ de verdade.
 *
 * <p>A mensagem e publicada diretamente na fila, sem passar pelo outbox: o que
 * esta em julgamento aqui e o comportamento de quem consome.</p>
 */
@TesteDeIntegracao
class ConsumidoresIT {

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private PedidoServico pedidoServico;

    @Autowired
    private ProdutoRepositorio produtos;

    @Autowired
    private UsuarioRepositorio usuarios;

    @Autowired
    private NotificacaoRepositorio notificacoes;

    @Autowired
    private JdbcTemplate jdbc;

    /**
     * Espera uma condicao se tornar verdadeira.
     *
     * <p>Consumo de fila e assincrono: sem esperar, o teste verificaria o banco
     * antes de o consumidor ter agido. Um {@code sleep} fixo seria lento no caso
     * bom e instavel no caso ruim; este laco sai assim que a condicao vale.</p>
     */
    private static void esperarAte(String oQue, BooleanSupplier condicao) {
        Instant limite = Instant.now().plus(Duration.ofSeconds(10));
        while (Instant.now().isBefore(limite)) {
            if (condicao.getAsBoolean()) {
                return;
            }
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
        }
        throw new AssertionError("Tempo esgotado esperando: " + oQue);
    }

    private Cenario novoCenario(int estoque, int quantidade) {
        Usuario cliente = usuarios.salvar(new Usuario("Cliente", "cliente-" + UUID.randomUUID() + "@exemplo.com",
                "$2a$10$hashfalsoparateste000000000000000000000000000000000000", Papel.CLIENTE, Instant.now()));
        Produto produto = produtos.salvar(
                new Produto("Produto " + UUID.randomUUID(), "d", new BigDecimal("100.00"), estoque));

        var resultado = pedidoServico.criar(
                new UsuarioAutenticado(cliente.getId(), cliente.getNome(), cliente.getPapel()),
                UUID.randomUUID().toString(),
                new PedidoRequisicao(List.of(new ItemRequisicao(produto.getId(), quantidade))));

        Pedido pedido = resultado.pedido();
        return new Cenario(pedido.getId(), cliente.getId(), produto.getId());
    }

    private record Cenario(Long pedidoId, Long usuarioId, Long produtoId) {
    }

    /** Publica o evento com o cabecalho de idempotencia, como o worker faria. */
    private void publicar(String idEvento, Cenario cenario) {
        String payload = """
                {"pedidoId":%d,"usuarioId":%d,"valorTotal":300.00,"idDaCobranca":"pi_teste","ocorridoEm":"2026-09-21T12:00:00Z"}"""
                .formatted(cenario.pedidoId(), cenario.usuarioId());

        // Publica do mesmo jeito que o worker do outbox publica: o conversor
        // padrao entrega a String ao listener. Montar a mensagem na mao com
        // content-type JSON faria o listener receber byte[] e falhar na
        // conversao — um teste que reprova o consumidor por um detalhe que a
        // producao nao tem.
        rabbitTemplate.convertAndSend(FilaConfig.EXCHANGE, EventosDePedido.PEDIDO_PAGO, payload,
                mensagem -> {
                    mensagem.getMessageProperties().setHeader("x-id-evento", idEvento);
                    return mensagem;
                });
    }

    private int consumosRegistrados(String idEvento) {
        Integer total = jdbc.queryForObject(
                "SELECT count(*) FROM mensagens_consumidas WHERE id_mensagem = ?", Integer.class, idEvento);
        return total == null ? 0 : total;
    }

    @Test
    @DisplayName("o evento baixa o estoque e notifica o cliente")
    void consomeOEvento() {
        Cenario cenario = novoCenario(10, 3);
        assertThat(produtos.porId(cenario.produtoId()).orElseThrow().getEstoqueReservado()).isEqualTo(3);

        publicar("evt_" + UUID.randomUUID(), cenario);

        esperarAte("o estoque ser baixado",
                () -> produtos.porId(cenario.produtoId()).orElseThrow().getEstoqueReservado() == 0);
        esperarAte("a notificacao ser criada",
                () -> !notificacoes.doPedido(cenario.pedidoId()).isEmpty());

        Produto produto = produtos.porId(cenario.produtoId()).orElseThrow();
        assertThat(produto.getEstoqueDisponivel()).isEqualTo(7);
        assertThat(produto.getEstoqueTotal()).as("as unidades sairam do deposito").isEqualTo(7);
        assertThat(notificacoes.doPedido(cenario.pedidoId())).hasSize(1);
    }

    @Test
    @DisplayName("a MESMA mensagem entregue tres vezes produz um unico efeito em cada consumidor")
    void mensagemRepetida() {
        Cenario cenario = novoCenario(10, 4);
        String idEvento = "evt_repetido_" + UUID.randomUUID();

        publicar(idEvento, cenario);
        esperarAte("o primeiro consumo", () -> consumosRegistrados(idEvento) == 2);

        // O broker reentrega o mesmo evento — porque o ack se perdeu, porque o
        // worker do outbox republicou, ou porque o consumidor morreu apos agir.
        publicar(idEvento, cenario);
        publicar(idEvento, cenario);

        // Esperar um pouco para que, se houvesse efeito duplicado, ele aparecesse.
        try {
            Thread.sleep(1500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        Produto produto = produtos.porId(cenario.produtoId()).orElseThrow();
        assertThat(produto.getEstoqueDisponivel())
                .as("o estoque baixou uma vez so: 10 - 4 reservadas, e as 4 sairam")
                .isEqualTo(6);
        assertThat(produto.getEstoqueReservado()).isZero();
        assertThat(produto.getEstoqueTotal()).isEqualTo(6);

        assertThat(notificacoes.doPedido(cenario.pedidoId()))
                .as("o cliente recebeu um unico aviso — e-mail duplicado nao tem rollback")
                .hasSize(1);

        assertThat(consumosRegistrados(idEvento))
                .as("um registro por consumidor: estoque e notificacao")
                .isEqualTo(2);
    }

    @Test
    @DisplayName("os dois consumidores tratam o mesmo evento, cada um com seu registro")
    void chaveIncluiOConsumidor() {
        Cenario cenario = novoCenario(10, 1);
        String idEvento = "evt_" + UUID.randomUUID();

        publicar(idEvento, cenario);

        esperarAte("os dois consumidores registrarem", () -> consumosRegistrados(idEvento) == 2);

        List<String> consumidores = jdbc.queryForList(
                "SELECT consumidor FROM mensagens_consumidas WHERE id_mensagem = ? ORDER BY consumidor",
                String.class, idEvento);

        assertThat(consumidores)
                .as("se a chave fosse so o id da mensagem, o segundo consumidor nunca agiria")
                .containsExactly("estoque", "notificacao");
    }

    @Test
    @DisplayName("mensagem sem chave de idempotencia vai para a fila de mortas")
    void mensagemSemChaveVaiParaMortas() {
        Cenario cenario = novoCenario(10, 1);

        String payload = """
                {"pedidoId":%d,"usuarioId":%d,"valorTotal":100.00,"idDaCobranca":"pi_x","ocorridoEm":"2026-09-21T12:00:00Z"}"""
                .formatted(cenario.pedidoId(), cenario.usuarioId());

        // Sem o cabecalho x-id-evento: sem chave, nao ha como garantir efeito
        // unico, e processar as cegas seria pior do que rejeitar.
        rabbitTemplate.convertAndSend(FilaConfig.EXCHANGE, EventosDePedido.PEDIDO_PAGO, payload);

        esperarAte("a mensagem chegar na fila de mortas do estoque",
                () -> rabbitTemplate.receive("pedidos.estoque.mortas", 200) != null);

        assertThat(produtos.porId(cenario.produtoId()).orElseThrow().getEstoqueReservado())
                .as("a mensagem recusada nao teve efeito nenhum")
                .isEqualTo(1);
    }
}
