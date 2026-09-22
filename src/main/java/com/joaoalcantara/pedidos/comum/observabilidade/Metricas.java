package com.joaoalcantara.pedidos.comum.observabilidade;

import org.springframework.stereotype.Component;

import com.joaoalcantara.pedidos.outbox.dominio.OutboxRepositorio;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;

/**
 * As metricas de negocio deste sistema, num lugar so.
 *
 * <p>O Actuator ja entrega de graca as metricas tecnicas — latencia HTTP, pool
 * de conexoes, memoria, GC. Elas respondem "a aplicacao esta saudavel?". As
 * daqui respondem outra pergunta, a que interessa quando o problema e silencioso:
 * <b>"o dinheiro esta entrando e as promessas estao sendo cumpridas?"</b></p>
 *
 * <p>A mais importante e {@code pedidos_outbox_pendentes}. Um pedido pago cujo
 * evento nao saiu do outbox e o pior tipo de falha: a API responde 200, o
 * cliente ve tudo certo, o health check fica verde — e o estoque nunca baixa,
 * o cliente nunca e avisado. Nenhuma metrica tecnica acusa isso. Uma fila de
 * pendentes que so cresce, acusa.</p>
 *
 * <p>Centralizar tambem evita o problema classico de metrica: nome inventado em
 * cada chamada, e ninguem consegue montar um painel porque
 * {@code pedido.criado} e {@code pedidos_criados} convivem.</p>
 */
@Component
public class Metricas {

    private final Counter pedidosCriados;
    private final Counter pedidosRepetidos;
    private final Counter pedidosExpirados;
    private final MeterRegistry registry;

    Metricas(MeterRegistry registry, OutboxRepositorio outbox) {
        this.registry = registry;

        this.pedidosCriados = Counter.builder("pedidos.criados")
                .description("Pedidos criados com sucesso")
                .register(registry);

        this.pedidosRepetidos = Counter.builder("pedidos.idempotencia.repetidos")
                .description("Requisicoes de criacao reconhecidas como reenvio da mesma chave")
                .register(registry);

        this.pedidosExpirados = Counter.builder("pedidos.expirados")
                .description("Pedidos cancelados por falta de pagamento no prazo")
                .register(registry);

        // Gauge le o valor a cada coleta. A consulta e barata por causa do
        // indice parcial sobre os pendentes — sem ele, esta metrica ficaria mais
        // cara conforme a tabela crescesse, que e exatamente o contrario do que
        // se espera de um medidor.
        Gauge.builder("pedidos.outbox.pendentes", outbox::contarPendentes)
                .description("Eventos aguardando publicacao. So cresce quando a entrega esta falhando.")
                .register(registry);
    }

    public void pedidoCriado() {
        pedidosCriados.increment();
    }

    public void pedidoRepetido() {
        pedidosRepetidos.increment();
    }

    public void pedidosExpirados(int quantidade) {
        pedidosExpirados.increment(quantidade);
    }

    /** Eventos do gateway, separados por desfecho. */
    public void eventoDoGateway(String resultado) {
        registry.counter("pedidos.webhook.eventos", "resultado", resultado).increment();
    }

    /** Publicacoes do outbox, separadas por desfecho. */
    public void publicacaoDoOutbox(String resultado) {
        registry.counter("pedidos.outbox.publicacoes", "resultado", resultado).increment();
    }

    /**
     * Consumo de mensagens, por consumidor e desfecho.
     *
     * <p>{@code resultado=repetido} subindo nao e problema — e a idempotencia
     * trabalhando. Subir <b>muito</b> e sinal de que alguma coisa esta
     * reentregando demais, e vale investigar.</p>
     */
    public void mensagemConsumida(String consumidor, String resultado) {
        registry.counter("pedidos.consumo.mensagens", "consumidor", consumidor, "resultado", resultado).increment();
    }
}
