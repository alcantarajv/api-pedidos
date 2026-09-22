package com.joaoalcantara.pedidos.manutencao;

import java.time.Clock;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.joaoalcantara.pedidos.consumo.dominio.MensagemConsumidaRepositorio;
import com.joaoalcantara.pedidos.outbox.dominio.OutboxRepositorio;
import com.joaoalcantara.pedidos.webhook.dominio.EventoProcessadoRepositorio;

/**
 * Apaga registros de idempotencia que ja passaram da validade.
 *
 * <p>As tres tabelas — eventos do gateway, mensagens consumidas e outbox ja
 * publicado — crescem para sempre e nunca sao lidas depois de alguns dias.
 * Deixa-las crescer torna cada consulta de idempotencia mais lenta justamente
 * no caminho quente: o do webhook e o do consumidor.</p>
 *
 * <p>O risco de apagar cedo demais e real: um evento antigo que voltasse seria
 * tratado como novo, e o efeito aconteceria duas vezes. Por isso a retencao e
 * folgada (trinta dias por padrao) — a janela de retentativa de qualquer
 * gateway serio se mede em horas.</p>
 */
@Component
public class LimpezaDeRegistros {

    private static final Logger log = LoggerFactory.getLogger(LimpezaDeRegistros.class);

    private final EventoProcessadoRepositorio eventosDoGateway;
    private final MensagemConsumidaRepositorio mensagensConsumidas;
    private final OutboxRepositorio outbox;
    private final PropriedadesDeLimpeza propriedades;
    private final Clock relogio;

    LimpezaDeRegistros(EventoProcessadoRepositorio eventosDoGateway,
                       MensagemConsumidaRepositorio mensagensConsumidas,
                       OutboxRepositorio outbox,
                       PropriedadesDeLimpeza propriedades,
                       Clock relogio) {
        this.eventosDoGateway = eventosDoGateway;
        this.mensagensConsumidas = mensagensConsumidas;
        this.outbox = outbox;
        this.propriedades = propriedades;
        this.relogio = relogio;
    }

    @Scheduled(fixedDelayString = "${pedidos.limpeza.intervalo-ms:3600000}")
    public void rodar() {
        limpar();
    }

    @Transactional
    public int limpar() {
        Instant limite = relogio.instant().minus(propriedades.retencao());

        int eventos = eventosDoGateway.apagarAnterioresA(limite);
        int mensagens = mensagensConsumidas.apagarAnterioresA(limite);
        // So os JA PUBLICADOS: um evento pendente de 40 dias e um problema para
        // investigar, nao lixo para apagar.
        int publicados = outbox.apagarPublicadosAnterioresA(limite);

        int total = eventos + mensagens + publicados;
        if (total > 0) {
            log.info("Limpeza: {} eventos do gateway, {} mensagens consumidas e {} linhas do outbox removidas.",
                    eventos, mensagens, publicados);
        }
        return total;
    }
}
