package com.joaoalcantara.pedidos.pedido;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import com.joaoalcantara.pedidos.comum.RelogioAjustavel;
import com.joaoalcantara.pedidos.comum.TesteDeIntegracao;
import com.joaoalcantara.pedidos.pagamento.dominio.Pagamento;
import com.joaoalcantara.pedidos.pagamento.dominio.PagamentoRepositorio;
import com.joaoalcantara.pedidos.pagamento.dominio.StatusPagamento;
import com.joaoalcantara.pedidos.pedido.aplicacao.ExpiradorDePedidos;
import com.joaoalcantara.pedidos.pedido.aplicacao.PedidoServico;
import com.joaoalcantara.pedidos.pedido.api.ItemRequisicao;
import com.joaoalcantara.pedidos.pedido.api.PedidoRequisicao;
import com.joaoalcantara.pedidos.pedido.dominio.Pedido;
import com.joaoalcantara.pedidos.pedido.dominio.PedidoRepositorio;
import com.joaoalcantara.pedidos.pedido.dominio.StatusPedido;
import com.joaoalcantara.pedidos.produto.dominio.Produto;
import com.joaoalcantara.pedidos.produto.dominio.ProdutoRepositorio;
import com.joaoalcantara.pedidos.seguranca.UsuarioAutenticado;
import com.joaoalcantara.pedidos.usuario.dominio.Papel;
import com.joaoalcantara.pedidos.usuario.dominio.Usuario;
import com.joaoalcantara.pedidos.usuario.dominio.UsuarioRepositorio;

/**
 * A expiracao de pedidos nao pagos, com o tempo sob controle.
 *
 * <p>O prazo configurado e de 30 minutos; o teste pula 31. Sem o {@code Clock}
 * injetado, a alternativa seria configurar um prazo de segundos e esperar de
 * verdade — um teste lento, instavel, e que verificaria uma configuracao que
 * nao e a de producao.</p>
 */
@TesteDeIntegracao
@Import(RelogioAjustavel.class)
class ExpiracaoDePedidosIT {

    @Autowired
    private ExpiradorDePedidos expirador;

    @Autowired
    private PedidoServico pedidoServico;

    @Autowired
    private PedidoRepositorio pedidos;

    @Autowired
    private ProdutoRepositorio produtos;

    @Autowired
    private UsuarioRepositorio usuarios;

    @Autowired
    private PagamentoRepositorio pagamentos;

    @Autowired
    private Clock relogio;

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbc;

    /**
     * O relogio ajustavel e um bean do contexto: o tempo que um teste avanca
     * continua avancado para o proximo. Sem limpar, um pedido criado la atras
     * apareceria como vencido aqui e entraria na contagem desta rodada.
     */
    @org.junit.jupiter.api.BeforeEach
    void limparPedidos() {
        jdbc.execute("TRUNCATE TABLE notificacoes, pagamentos, itens_pedido, pedidos, produtos, usuarios RESTART IDENTITY CASCADE");
    }

    private RelogioAjustavel.ClockMovel relogioMovel() {
        return (RelogioAjustavel.ClockMovel) relogio;
    }

    private Cenario novoPedido(int estoque, int quantidade) {
        Usuario cliente = usuarios.salvar(new Usuario("Cliente", "cliente-" + UUID.randomUUID() + "@exemplo.com",
                "$2a$10$hashfalsoparateste000000000000000000000000000000000000", Papel.CLIENTE, Instant.now()));
        Produto produto = produtos.salvar(
                new Produto("Produto " + UUID.randomUUID(), "d", new BigDecimal("100.00"), estoque));

        var resultado = pedidoServico.criar(
                new UsuarioAutenticado(cliente.getId(), cliente.getNome(), cliente.getPapel()),
                UUID.randomUUID().toString(),
                new PedidoRequisicao(List.of(new ItemRequisicao(produto.getId(), quantidade))));

        return new Cenario(resultado.pedido(), produto.getId(), cliente);
    }

    private record Cenario(Pedido pedido, Long produtoId, Usuario cliente) {
    }

    @Test
    @DisplayName("pedido nao pago alem do prazo e cancelado e devolve o estoque")
    void expiraPedidoVencido() {
        Cenario cenario = novoPedido(10, 3);
        assertThat(produtos.porId(cenario.produtoId()).orElseThrow().getEstoqueDisponivel()).isEqualTo(7);

        relogioMovel().avancar(Duration.ofMinutes(31));
        int expirados = expirador.expirarVencidos();

        assertThat(expirados).isEqualTo(1);
        assertThat(pedidos.porId(cenario.pedido().getId()).orElseThrow().getStatus())
                .isEqualTo(StatusPedido.CANCELADO);

        Produto produto = produtos.porId(cenario.produtoId()).orElseThrow();
        assertThat(produto.getEstoqueDisponivel())
                .as("as unidades voltam para quem quiser comprar agora")
                .isEqualTo(10);
        assertThat(produto.getEstoqueReservado()).isZero();
    }

    @Test
    @DisplayName("pedido dentro do prazo nao e tocado")
    void naoExpiraPedidoRecente() {
        Cenario cenario = novoPedido(10, 2);

        relogioMovel().avancar(Duration.ofMinutes(29));
        int expirados = expirador.expirarVencidos();

        assertThat(expirados).isZero();
        assertThat(pedidos.porId(cenario.pedido().getId()).orElseThrow().getStatus())
                .isEqualTo(StatusPedido.AGUARDANDO_PAGAMENTO);
        assertThat(produtos.porId(cenario.produtoId()).orElseThrow().getEstoqueReservado()).isEqualTo(2);
    }

    @Test
    @DisplayName("pedido ja pago nunca expira, por mais tempo que passe")
    void naoExpiraPedidoPago() {
        Cenario cenario = novoPedido(10, 2);
        Pedido pedido = pedidos.porIdComItens(cenario.pedido().getId()).orElseThrow();
        pedido.marcarComoPago();
        pedidos.salvar(pedido);

        relogioMovel().avancar(Duration.ofDays(30));
        int expirados = expirador.expirarVencidos();

        assertThat(expirados).isZero();
        assertThat(pedidos.porId(cenario.pedido().getId()).orElseThrow().getStatus())
                .isEqualTo(StatusPedido.PAGO);
    }

    @Test
    @DisplayName("pedido ja cancelado nao e cancelado de novo")
    void naoExpiraPedidoCancelado() {
        Cenario cenario = novoPedido(10, 2);
        pedidoServico.cancelar(
                new UsuarioAutenticado(cenario.cliente().getId(), cenario.cliente().getNome(), Papel.CLIENTE),
                cenario.pedido().getId());

        relogioMovel().avancar(Duration.ofHours(2));
        int expirados = expirador.expirarVencidos();

        assertThat(expirados).isZero();
        assertThat(produtos.porId(cenario.produtoId()).orElseThrow().getEstoqueDisponivel())
                .as("o estoque devolvido no cancelamento nao pode voltar duas vezes")
                .isEqualTo(10);
    }

    /**
     * A consulta, verificada diretamente.
     *
     * <p>Os outros testes provam o comportamento, e continuariam passando mesmo
     * sem o filtro de status na consulta — porque a maquina de estados recusaria
     * a transicao de um pedido pago e o expirador trataria isso como "nao
     * expirou". Correto, porem desperdicado: o job carregaria e travaria linhas
     * so para ve-las recusadas. Este teste guarda a consulta em si.</p>
     */
    @Test
    @DisplayName("a consulta so devolve pedidos que ainda aguardam pagamento")
    void consultaFiltraPorStatus() {
        Cenario aguardando = novoPedido(10, 1);

        Cenario pago = novoPedido(10, 1);
        Pedido pedidoPago = pedidos.porIdComItens(pago.pedido().getId()).orElseThrow();
        pedidoPago.marcarComoPago();
        pedidos.salvar(pedidoPago);

        Cenario cancelado = novoPedido(10, 1);
        pedidoServico.cancelar(
                new UsuarioAutenticado(cancelado.cliente().getId(), cancelado.cliente().getNome(), Papel.CLIENTE),
                cancelado.pedido().getId());

        relogioMovel().avancar(Duration.ofHours(1));

        List<Long> vencidos = pedidos.idsAguardandoPagamentoDesdeAntesDe(relogio.instant(), 100);

        assertThat(vencidos)
                .containsExactly(aguardando.pedido().getId())
                .doesNotContain(pago.pedido().getId(), cancelado.pedido().getId());
    }

    @Test
    @DisplayName("expira varios pedidos numa rodada, sem um atrapalhar o outro")
    void expiraVarios() {
        Cenario primeiro = novoPedido(10, 1);
        Cenario segundo = novoPedido(10, 2);
        Cenario terceiro = novoPedido(10, 3);

        relogioMovel().avancar(Duration.ofHours(1));
        int expirados = expirador.expirarVencidos();

        assertThat(expirados).isGreaterThanOrEqualTo(3);
        for (Cenario cenario : List.of(primeiro, segundo, terceiro)) {
            assertThat(pedidos.porId(cenario.pedido().getId()).orElseThrow().getStatus())
                    .isEqualTo(StatusPedido.CANCELADO);
            assertThat(produtos.porId(cenario.produtoId()).orElseThrow().getEstoqueReservado()).isZero();
        }
    }

    @Test
    @DisplayName("a cobranca criada no gateway nao impede a expiracao")
    void expiraMesmoComCobrancaPendente() {
        Cenario cenario = novoPedido(10, 2);
        pagamentos.salvar(new Pagamento(cenario.pedido(), "pi_" + UUID.randomUUID(),
                cenario.pedido().getValorTotal(), StatusPagamento.PENDENTE, Instant.now()));

        relogioMovel().avancar(Duration.ofHours(1));

        assertThat(expirador.expirarVencidos()).isEqualTo(1);
        assertThat(pedidos.porId(cenario.pedido().getId()).orElseThrow().getStatus())
                .isEqualTo(StatusPedido.CANCELADO);
        // Cobranca pendente nao vira paga sozinha. Se o cliente pagar depois
        // disso, o webhook encontra um pedido CANCELADO, recusa a transicao e o
        // caso vira reembolso — decidido por gente, nao por um job.
        assertThat(pagamentos.porPedido(cenario.pedido().getId()).orElseThrow().getStatus())
                .isEqualTo(StatusPagamento.PENDENTE);
    }
}
