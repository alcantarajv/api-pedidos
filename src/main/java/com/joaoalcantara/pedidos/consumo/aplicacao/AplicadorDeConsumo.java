package com.joaoalcantara.pedidos.consumo.aplicacao;

import java.time.Clock;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.joaoalcantara.pedidos.consumo.dominio.MensagemConsumida;
import com.joaoalcantara.pedidos.consumo.dominio.MensagemConsumidaRepositorio;

/**
 * O registro de consumo e o efeito, na mesma transacao.
 *
 * <p>Bean separado de {@link ConsumoIdempotente} porque {@code @Transactional}
 * so vale em chamada que passa pelo proxy do Spring: se este metodo fosse
 * privado na outra classe, a anotacao seria ignorada em silencio e o registro
 * commitaria independentemente do efeito — exatamente o que o padrao existe
 * para impedir.</p>
 */
@Component
class AplicadorDeConsumo {

    private final MensagemConsumidaRepositorio registros;
    private final Clock relogio;

    AplicadorDeConsumo(MensagemConsumidaRepositorio registros, Clock relogio) {
        this.registros = registros;
        this.relogio = relogio;
    }

    @Transactional
    void aplicar(String idMensagem, String consumidor, Runnable efeito) {
        // Primeiro o registro: se colidir, o efeito nao chega a acontecer.
        registros.salvar(new MensagemConsumida(idMensagem, consumidor, relogio.instant()));
        efeito.run();
    }
}
