package com.joaoalcantara.pedidos.consumo.aplicacao;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import com.joaoalcantara.pedidos.consumo.dominio.MensagemConsumidaRepositorio;

/**
 * Executa o efeito de uma mensagem no maximo uma vez por consumidor.
 *
 * <p>E o mesmo mecanismo do webhook, do outro lado da conversa: o registro de
 * consumo e o efeito vao na mesma transacao ({@link AplicadorDeConsumo}). Se o
 * efeito falhar, o registro some junto e a mensagem sera reentregue; se ela ja
 * tiver sido consumida, o {@code INSERT} colide no indice unico e nada
 * acontece.</p>
 *
 * <p>A classe existe porque a regra vale para <b>todo</b> consumidor. Deixar
 * cada um implementar a propria idempotencia garantiria que, mais cedo ou mais
 * tarde, algum a implementaria errado — e seria justamente o que manda e-mail
 * duplicado para o cliente.</p>
 *
 * <p>Nao e transacional: o tratamento da colisao precisa acontecer depois que a
 * transacao que falhou terminou.</p>
 */
@Component
public class ConsumoIdempotente {

    private static final Logger log = LoggerFactory.getLogger(ConsumoIdempotente.class);

    private final MensagemConsumidaRepositorio registros;
    private final AplicadorDeConsumo aplicador;

    ConsumoIdempotente(MensagemConsumidaRepositorio registros, AplicadorDeConsumo aplicador) {
        this.registros = registros;
        this.aplicador = aplicador;
    }

    /**
     * @return {@code true} se o efeito foi aplicado agora; {@code false} se a
     *         mensagem ja havia sido consumida por este consumidor
     */
    public boolean executar(String idMensagem, String consumidor, Runnable efeito) {
        // Consulta previa: resolve a reentrega comum sem provocar erro no banco.
        if (registros.jaConsumida(idMensagem, consumidor)) {
            log.debug("Mensagem {} ja consumida por {}; ignorando repeticao.", idMensagem, consumidor);
            return false;
        }

        try {
            aplicador.aplicar(idMensagem, consumidor, efeito);
            return true;
        } catch (DataIntegrityViolationException e) {
            // Nem toda violacao de integridade e entrega duplicada: campo grande
            // demais, chave estrangeira quebrada e coluna obrigatoria vazia
            // chegam aqui com o mesmo tipo de excecao. Tratar todas como
            // "repetido" transformaria defeito em silencio — a mensagem seria
            // descartada com um log de debug e ninguem saberia.
            //
            // A confirmacao e barata: se o registro existe agora, era mesmo uma
            // corrida entre duas entregas. Se nao existe, o erro e outro e
            // precisa estourar — a mensagem volta para a fila, e o que nao der
            // certo acaba na fila de mortas, onde e visivel.
            if (!registros.jaConsumida(idMensagem, consumidor)) {
                throw e;
            }
            log.debug("Mensagem {} consumida simultaneamente por outra entrega de {}.", idMensagem, consumidor);
            return false;
        }
    }
}
