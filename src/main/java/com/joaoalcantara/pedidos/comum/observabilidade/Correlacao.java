package com.joaoalcantara.pedidos.comum.observabilidade;

import java.util.UUID;

import org.slf4j.MDC;

/**
 * O fio que liga todas as linhas de log de um mesmo pedido.
 *
 * <p>Um pedido pago atravessa quatro processos: a requisicao HTTP do cliente, o
 * webhook do gateway, o worker do outbox e os consumidores da fila. Cada um
 * roda numa thread diferente, em momento diferente. Sem um identificador comum,
 * investigar "o que aconteceu com o pedido 4711" e abrir quatro janelas de log e
 * cruzar horarios na mao.</p>
 *
 * <p>O valor vive no {@link MDC} do SLF4J — um mapa por thread que o formato de
 * log estruturado anexa automaticamente a toda linha, sem que nenhuma chamada de
 * {@code log.info} precise mencionar o assunto.</p>
 *
 * <p>Por ser por thread, ele <b>nao</b> atravessa fronteiras sozinho: quem
 * publica precisa colocar o id na mensagem, e quem consome precisa recoloca-lo
 * no MDC. E o que {@code PublicadorRabbit} e os consumidores fazem.</p>
 */
public final class Correlacao {

    /** Nome da chave no MDC e no log estruturado. */
    public static final String CHAVE = "correlacaoId";

    /** Cabecalho HTTP de entrada e saida. */
    public static final String CABECALHO = "X-Request-Id";

    /** Cabecalho da mensagem AMQP. */
    public static final String CABECALHO_AMQP = "x-correlacao-id";

    private Correlacao() {
    }

    public static String atual() {
        return MDC.get(CHAVE);
    }

    public static String gerar() {
        return UUID.randomUUID().toString();
    }

    /**
     * Executa o trecho com o id no MDC e devolve o MDC ao estado anterior.
     *
     * <p>Restaurar em vez de limpar importa em pool de threads: a thread e
     * reaproveitada, e um {@code MDC.clear()} descuidado apagaria o contexto de
     * quem a chamou.</p>
     */
    public static void executarCom(String correlacaoId, Runnable trecho) {
        String anterior = MDC.get(CHAVE);
        MDC.put(CHAVE, correlacaoId == null || correlacaoId.isBlank() ? gerar() : correlacaoId);
        try {
            trecho.run();
        } finally {
            if (anterior == null) {
                MDC.remove(CHAVE);
            } else {
                MDC.put(CHAVE, anterior);
            }
        }
    }
}
