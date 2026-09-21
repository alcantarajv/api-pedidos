package com.joaoalcantara.pedidos.pedido;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.joaoalcantara.pedidos.comum.TesteDeIntegracao;
import com.joaoalcantara.pedidos.pedido.api.ItemRequisicao;
import com.joaoalcantara.pedidos.pedido.api.PedidoRequisicao;
import com.joaoalcantara.pedidos.pedido.aplicacao.PedidoServico;
import com.joaoalcantara.pedidos.produto.dominio.EstoqueInsuficienteException;
import com.joaoalcantara.pedidos.produto.dominio.Produto;
import com.joaoalcantara.pedidos.produto.dominio.ProdutoRepositorio;
import com.joaoalcantara.pedidos.seguranca.UsuarioAutenticado;
import com.joaoalcantara.pedidos.usuario.dominio.Papel;
import com.joaoalcantara.pedidos.usuario.dominio.Usuario;
import com.joaoalcantara.pedidos.usuario.dominio.UsuarioRepositorio;

/**
 * As duas corridas da criacao de pedido.
 *
 * <p>Uma consulta antes de gravar nunca fecha a janela entre consultar e gravar.
 * Os dois testes aqui disparam N threads simultaneas e verificam o estado final
 * do banco — que e o unico juiz confiavel: contador em memoria pode mentir, a
 * tabela nao.</p>
 */
@TesteDeIntegracao
class PedidoConcorrenteIT {

    private static final int THREADS = 12;

    @Autowired
    private PedidoServico pedidoServico;

    @Autowired
    private ProdutoRepositorio produtos;

    @Autowired
    private UsuarioRepositorio usuarios;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void limpar() {
        // Ordem importa: itens referenciam pedidos, que referenciam usuarios e produtos.
        jdbc.execute("TRUNCATE TABLE itens_pedido, pedidos, produtos, usuarios RESTART IDENTITY CASCADE");
    }

    private Usuario novoCliente(int indice) {
        return usuarios.salvar(new Usuario("Cliente " + indice, "cliente%d@teste.dev".formatted(indice),
                "$2a$10$hashfalsoparateste000000000000000000000000000000000000", Papel.CLIENTE, Instant.now()));
    }

    private UsuarioAutenticado autenticado(Usuario usuario) {
        return new UsuarioAutenticado(usuario.getId(), usuario.getNome(), usuario.getPapel());
    }

    private PedidoRequisicao pedidoDe(Long produtoId, int quantidade) {
        return new PedidoRequisicao(List.of(new ItemRequisicao(produtoId, quantidade)));
    }

    /**
     * Dispara a tarefa em N threads soltas ao mesmo tempo.
     *
     * <p>A largada e o que faz deste um teste de concorrencia: sem ela as threads
     * comecariam em sequencia e o teste passaria sem nunca ter havido disputa.</p>
     */
    private void emParalelo(int quantidade, TarefaIndexada tarefa) throws InterruptedException {
        CountDownLatch largada = new CountDownLatch(1);
        CountDownLatch chegada = new CountDownLatch(quantidade);

        try (ExecutorService executor = Executors.newFixedThreadPool(quantidade)) {
            for (int i = 0; i < quantidade; i++) {
                int indice = i;
                executor.submit(() -> {
                    try {
                        largada.await();
                        tarefa.executar(indice);
                    } catch (Throwable ignorado) {
                        // Cada teste decide o que fazer com as falhas.
                    } finally {
                        chegada.countDown();
                    }
                });
            }
            largada.countDown();
            assertThat(chegada.await(60, TimeUnit.SECONDS))
                    .as("todas as threads terminaram dentro do tempo")
                    .isTrue();
        }
    }

    @Test
    @DisplayName("mesma chave de idempotencia em N requisicoes simultaneas cria um unico pedido")
    void mesmaChaveCriaUmUnicoPedido() throws InterruptedException {
        Usuario cliente = novoCliente(0);
        Produto produto = produtos.salvar(new Produto("Item disputado", "d", new BigDecimal("100.00"), 500));
        String chave = UUID.randomUUID().toString();

        List<Long> idsDevolvidos = Collections.synchronizedList(new ArrayList<>());
        List<Throwable> falhas = Collections.synchronizedList(new ArrayList<>());

        emParalelo(THREADS, indice -> {
            try {
                var resultado = pedidoServico.criar(autenticado(cliente), chave, pedidoDe(produto.getId(), 1));
                idsDevolvidos.add(resultado.pedido().getId());
            } catch (Throwable e) {
                falhas.add(e);
                throw e;
            }
        });

        assertThat(falhas)
                .as("um reenvio concorrente nao e erro: todas as chamadas devem responder normalmente")
                .isEmpty();

        assertThat(idsDevolvidos)
                .as("todas as chamadas devem apontar para o mesmo pedido")
                .hasSize(THREADS)
                .containsOnly(idsDevolvidos.get(0));

        Integer gravados = jdbc.queryForObject("SELECT count(*) FROM pedidos", Integer.class);
        assertThat(gravados).as("apenas um pedido deve existir no banco").isEqualTo(1);

        // A prova de que o efeito colateral tambem aconteceu uma so vez.
        Produto depois = produtos.porId(produto.getId()).orElseThrow();
        assertThat(depois.getEstoqueReservado()).isEqualTo(1);
        assertThat(depois.getEstoqueDisponivel()).isEqualTo(499);
    }

    @Test
    @DisplayName("N clientes disputando a ultima unidade: exatamente um leva")
    void ultimaUnidadeVaiParaUmSoCliente() throws InterruptedException {
        Produto produto = produtos.salvar(new Produto("Ultima unidade", "d", new BigDecimal("100.00"), 1));
        List<Usuario> clientes = new ArrayList<>();
        for (int i = 0; i < THREADS; i++) {
            clientes.add(novoCliente(i));
        }

        AtomicInteger sucessos = new AtomicInteger();
        AtomicInteger semEstoque = new AtomicInteger();
        List<Throwable> inesperados = Collections.synchronizedList(new ArrayList<>());

        emParalelo(THREADS, indice -> {
            try {
                pedidoServico.criar(autenticado(clientes.get(indice)),
                        UUID.randomUUID().toString(), pedidoDe(produto.getId(), 1));
                sucessos.incrementAndGet();
            } catch (Throwable e) {
                if (ehEstoqueInsuficiente(e)) {
                    semEstoque.incrementAndGet();
                } else {
                    inesperados.add(e);
                }
            }
        });

        assertThat(inesperados)
                .as("nenhuma thread deve falhar por motivo diferente de estoque insuficiente")
                .isEmpty();
        assertThat(sucessos.get()).as("exatamente um cliente leva a ultima unidade").isEqualTo(1);
        assertThat(semEstoque.get()).isEqualTo(THREADS - 1);

        Produto depois = produtos.porId(produto.getId()).orElseThrow();
        assertThat(depois.getEstoqueDisponivel()).as("o estoque nunca fica negativo").isZero();
        assertThat(depois.getEstoqueReservado()).isEqualTo(1);

        Integer pedidosGravados = jdbc.queryForObject("SELECT count(*) FROM pedidos", Integer.class);
        assertThat(pedidosGravados).isEqualTo(1);
    }

    @Test
    @DisplayName("N cancelamentos simultaneos do mesmo pedido devolvem o estoque uma unica vez")
    void cancelamentoConcorrenteDevolveUmaVez() throws InterruptedException {
        Usuario cliente = novoCliente(0);
        Produto produto = produtos.salvar(new Produto("Item", "d", new BigDecimal("100.00"), 10));
        var criado = pedidoServico.criar(autenticado(cliente), UUID.randomUUID().toString(),
                pedidoDe(produto.getId(), 4));
        Long pedidoId = criado.pedido().getId();

        AtomicInteger cancelamentos = new AtomicInteger();
        AtomicInteger recusados = new AtomicInteger();

        emParalelo(THREADS, indice -> {
            try {
                pedidoServico.cancelar(autenticado(cliente), pedidoId);
                cancelamentos.incrementAndGet();
            } catch (Throwable e) {
                recusados.incrementAndGet();
            }
        });

        assertThat(cancelamentos.get()).as("so um cancelamento vale").isEqualTo(1);
        assertThat(recusados.get()).isEqualTo(THREADS - 1);

        Produto depois = produtos.porId(produto.getId()).orElseThrow();
        assertThat(depois.getEstoqueDisponivel())
                .as("o estoque volta exatamente uma vez, nao uma por thread")
                .isEqualTo(10);
        assertThat(depois.getEstoqueReservado()).isZero();
    }

    /**
     * A excecao pode chegar embrulhada por camadas do Spring. Procurar na cadeia
     * de causas evita um teste fragil que depende do embrulho exato.
     */
    private boolean ehEstoqueInsuficiente(Throwable erro) {
        for (Throwable causa = erro; causa != null; causa = causa.getCause()) {
            if (causa instanceof EstoqueInsuficienteException) {
                return true;
            }
            if (causa == causa.getCause()) {
                break;
            }
        }
        return false;
    }

    @FunctionalInterface
    private interface TarefaIndexada {
        void executar(int indice) throws Exception;
    }
}
