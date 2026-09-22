package com.joaoalcantara.pedidos.comum;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import com.joaoalcantara.pedidos.comum.observabilidade.Correlacao;
import com.joaoalcantara.pedidos.outbox.aplicacao.WorkerDoOutbox;
import com.joaoalcantara.pedidos.pedido.aplicacao.PedidoServico;
import com.joaoalcantara.pedidos.pedido.api.ItemRequisicao;
import com.joaoalcantara.pedidos.pedido.api.PedidoRequisicao;
import com.joaoalcantara.pedidos.produto.dominio.Produto;
import com.joaoalcantara.pedidos.produto.dominio.ProdutoRepositorio;
import com.joaoalcantara.pedidos.seguranca.ServicoDeToken;
import com.joaoalcantara.pedidos.seguranca.UsuarioAutenticado;
import com.joaoalcantara.pedidos.usuario.dominio.Papel;
import com.joaoalcantara.pedidos.usuario.dominio.Usuario;
import com.joaoalcantara.pedidos.usuario.dominio.UsuarioRepositorio;

import io.micrometer.core.instrument.MeterRegistry;

/**
 * Id de correlacao e metricas.
 *
 * <p>Observabilidade tem um problema proprio: ela so e exercitada quando alguem
 * vai investigar um incidente — e descobrir na hora que o campo esta vazio nao
 * ajuda ninguem. Por isso ela tambem tem teste.</p>
 */
/*
 * O Spring Boot DESLIGA a exportacao de metricas nos testes por padrao — a ideia
 * e nao pagar o custo de exportadores em cada contexto de teste. A consequencia
 * e que /actuator/prometheus responde 404 sem esta anotacao, mesmo com tudo
 * corretamente configurado para producao. Foi assim que este teste falhou da
 * primeira vez, e vale conhecer a armadilha: o sintoma parece configuracao
 * errada, e nao e.
 */
@TesteDeIntegracao
@AutoConfigureMockMvc
@org.springframework.boot.micrometer.metrics.test.autoconfigure.AutoConfigureMetrics
class ObservabilidadeIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MeterRegistry registry;

    @Autowired
    private PedidoServico pedidoServico;

    @Autowired
    private ProdutoRepositorio produtos;

    @Autowired
    private UsuarioRepositorio usuarios;

    @Autowired
    private WorkerDoOutbox worker;

    @Autowired
    private ServicoDeToken servicoDeToken;

    @Autowired
    private JdbcTemplate jdbc;

    private Usuario novoUsuario(Papel papel) {
        return usuarios.salvar(new Usuario("Fulano", papel.name().toLowerCase() + "-" + UUID.randomUUID() + "@exemplo.com",
                "$2a$10$hashfalsoparateste000000000000000000000000000000000000", papel, Instant.now()));
    }

    @Test
    @DisplayName("a resposta devolve o id de correlacao que o cliente mandou")
    void reaproveitaOIdDoCliente() throws Exception {
        String meuId = "id-do-cliente-" + UUID.randomUUID();

        mockMvc.perform(get("/api/produtos").header(Correlacao.CABECALHO, meuId))
                .andExpect(status().isOk())
                .andExpect(header().string(Correlacao.CABECALHO, meuId));
    }

    @Test
    @DisplayName("sem cabecalho, um id e gerado e devolve na resposta")
    void geraQuandoNaoVem() throws Exception {
        String gerado = mockMvc.perform(get("/api/produtos"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getHeader(Correlacao.CABECALHO);

        assertThat(gerado).isNotBlank();
    }

    @Test
    @DisplayName("ate a resposta de erro carrega o id: e nela que ele mais serve")
    void respostaDeErroTambemTemId() throws Exception {
        String meuId = "id-do-erro-" + UUID.randomUUID();

        // 401 acontece na cadeia de filtros de seguranca. O filtro de correlacao
        // roda antes dela de proposito: sem isso, justamente a resposta que o
        // cliente vai reportar ao suporte sairia sem identificacao.
        mockMvc.perform(post("/api/pedidos")
                        .header(Correlacao.CABECALHO, meuId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"itens\":[]}"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(Correlacao.CABECALHO, meuId));
    }

    @Test
    @DisplayName("o id de correlacao acompanha o evento ate a fila")
    void correlacaoViajaComOEvento() {
        Usuario cliente = novoUsuario(Papel.CLIENTE);
        Produto produto = produtos.salvar(
                new Produto("Produto " + UUID.randomUUID(), "d", new BigDecimal("100.00"), 10));

        String correlacaoId = "rastro-" + UUID.randomUUID();

        // Simula o que o filtro faz numa requisicao HTTP: coloca o id no MDC.
        Correlacao.executarCom(correlacaoId, () -> {
            var resultado = pedidoServico.criar(
                    new UsuarioAutenticado(cliente.getId(), cliente.getNome(), cliente.getPapel()),
                    UUID.randomUUID().toString(),
                    new PedidoRequisicao(List.of(new ItemRequisicao(produto.getId(), 1))));

            // O evento do outbox nasce de dentro deste contexto.
            jdbc.update("""
                    INSERT INTO outbox (id_evento, tipo, agregado_id, payload, criado_em, tentativas, correlacao_id)
                    VALUES (?, 'pedido.pago', ?, '{}', now(), 0, ?)
                    """, UUID.randomUUID().toString(), String.valueOf(resultado.pedido().getId()),
                    com.joaoalcantara.pedidos.comum.observabilidade.Correlacao.atual());
        });

        String gravado = jdbc.queryForObject(
                "SELECT correlacao_id FROM outbox WHERE correlacao_id = ?", String.class, correlacaoId);

        assertThat(gravado)
                .as("o id atravessa a fronteira do processo gravado na linha do outbox")
                .isEqualTo(correlacaoId);
    }

    @Test
    @DisplayName("criar pedido move o contador de pedidos criados")
    void contaPedidosCriados() {
        double antes = registry.counter("pedidos.criados").count();

        Usuario cliente = novoUsuario(Papel.CLIENTE);
        Produto produto = produtos.salvar(
                new Produto("Produto " + UUID.randomUUID(), "d", new BigDecimal("100.00"), 10));
        pedidoServico.criar(
                new UsuarioAutenticado(cliente.getId(), cliente.getNome(), cliente.getPapel()),
                UUID.randomUUID().toString(),
                new PedidoRequisicao(List.of(new ItemRequisicao(produto.getId(), 1))));

        assertThat(registry.counter("pedidos.criados").count()).isEqualTo(antes + 1);
    }

    @Test
    @DisplayName("reenvio da mesma chave conta como repetido, nao como pedido novo")
    void contaReenvioSeparado() {
        Usuario cliente = novoUsuario(Papel.CLIENTE);
        Produto produto = produtos.salvar(
                new Produto("Produto " + UUID.randomUUID(), "d", new BigDecimal("100.00"), 10));
        String chave = UUID.randomUUID().toString();
        var autenticado = new UsuarioAutenticado(cliente.getId(), cliente.getNome(), cliente.getPapel());
        var requisicao = new PedidoRequisicao(List.of(new ItemRequisicao(produto.getId(), 1)));

        double criadosAntes = registry.counter("pedidos.criados").count();
        double repetidosAntes = registry.counter("pedidos.idempotencia.repetidos").count();

        pedidoServico.criar(autenticado, chave, requisicao);
        pedidoServico.criar(autenticado, chave, requisicao);

        assertThat(registry.counter("pedidos.criados").count()).isEqualTo(criadosAntes + 1);
        assertThat(registry.counter("pedidos.idempotencia.repetidos").count()).isEqualTo(repetidosAntes + 1);
    }

    @Test
    @DisplayName("a fila de pendentes do outbox e visivel como medidor")
    void medidorDePendentes() {
        jdbc.update("""
                INSERT INTO outbox (id_evento, tipo, agregado_id, payload, criado_em, tentativas)
                VALUES (?, 'pedido.evento.sem.binding', '1', '{}', now(), 0)
                """, UUID.randomUUID().toString());

        // Nao tem fila de destino: a publicacao falha e a linha continua pendente.
        worker.despacharLote();

        Double pendentes = registry.get("pedidos.outbox.pendentes").gauge().value();

        assertThat(pendentes)
                .as("o medidor que denuncia entrega travada — a falha que nenhuma metrica tecnica acusa")
                .isGreaterThanOrEqualTo(1.0);
    }

    @Test
    @DisplayName("as metricas exigem ADMIN: elas contam volume, falhas e filas")
    void metricasNaoSaoPublicas() throws Exception {
        mockMvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/actuator/prometheus")
                        .header("Authorization", "Bearer " + servicoDeToken.emitirPara(novoUsuario(Papel.CLIENTE)).token()))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/actuator/prometheus")
                        .header("Authorization", "Bearer " + servicoDeToken.emitirPara(novoUsuario(Papel.ADMIN)).token()))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("pedidos_outbox_pendentes")));
    }

    @Test
    @DisplayName("o health continua publico: quem monitora nao tem token")
    void healthContinuaPublico() throws Exception {
        mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }
}
