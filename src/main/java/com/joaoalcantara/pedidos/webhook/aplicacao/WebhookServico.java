package com.joaoalcantara.pedidos.webhook.aplicacao;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import com.joaoalcantara.pedidos.webhook.dominio.EventoProcessadoRepositorio;
import com.joaoalcantara.pedidos.webhook.infra.LeitorDeEvento;
import com.joaoalcantara.pedidos.webhook.infra.VerificadorDeAssinatura;

/**
 * Porta de entrada dos webhooks do gateway.
 *
 * <p>A ordem das tres etapas nao e negociavel:</p>
 *
 * <ol>
 *   <li><b>Verificar a assinatura</b> — antes de qualquer parsing. Desserializar
 *       um corpo nao confiavel e trabalho feito para quem talvez seja um
 *       atacante.</li>
 *   <li><b>Consultar se ja foi processado</b> — resolve o caso comum, a
 *       reentrega minutos depois, sem provocar erro no banco.</li>
 *   <li><b>Processar numa transacao com o indice unico</b> — fecha a janela que
 *       a consulta nao alcanca.</li>
 * </ol>
 *
 * <p>Este metodo <b>nao</b> e transacional de proposito. Quando o indice unico
 * e violado, a transacao fica marcada para rollback e o PostgreSQL recusa
 * qualquer comando seguinte nela; tratar a colisao exige estar fora dela. E a
 * mesma armadilha da criacao de pedido, na Etapa 4.</p>
 */
@Service
public class WebhookServico {

    private static final Logger log = LoggerFactory.getLogger(WebhookServico.class);

    private final VerificadorDeAssinatura verificador;
    private final LeitorDeEvento leitor;
    private final EventoProcessadoRepositorio eventos;
    private final ProcessadorDeEvento processador;

    public WebhookServico(VerificadorDeAssinatura verificador, LeitorDeEvento leitor,
                          EventoProcessadoRepositorio eventos, ProcessadorDeEvento processador) {
        this.verificador = verificador;
        this.leitor = leitor;
        this.eventos = eventos;
        this.processador = processador;
    }

    /**
     * @return o que aconteceu com o evento, para o controller registrar no log —
     *         a resposta HTTP e 200 nos dois casos
     */
    public Resultado receber(String corpoCru, String assinatura) {
        verificador.verificar(corpoCru, assinatura);

        EventoDoGateway evento = leitor.ler(corpoCru);

        if (eventos.jaProcessado(evento.id())) {
            log.info("Evento '{}' ja havia sido processado; nada a fazer.", evento.id());
            return Resultado.REPETIDO;
        }

        try {
            processador.processar(evento);
            return Resultado.PROCESSADO;
        } catch (DataIntegrityViolationException e) {
            // Duas entregas do mesmo evento ao mesmo tempo: a outra venceu a
            // corrida e ja aplicou o efeito. Nao e erro — e exatamente o que a
            // restricao de unicidade existe para produzir.
            log.info("Evento '{}' processado simultaneamente por outra entrega.", evento.id());
            return Resultado.REPETIDO;
        }
    }

    public enum Resultado {
        PROCESSADO,
        REPETIDO
    }
}
