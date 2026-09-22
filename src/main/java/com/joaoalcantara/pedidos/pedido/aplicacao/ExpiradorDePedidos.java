package com.joaoalcantara.pedidos.pedido.aplicacao;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.joaoalcantara.pedidos.comum.config.PropriedadesDeExpiracao;
import com.joaoalcantara.pedidos.comum.observabilidade.Correlacao;
import com.joaoalcantara.pedidos.comum.observabilidade.Metricas;
import com.joaoalcantara.pedidos.pedido.dominio.PedidoRepositorio;

/**
 * Cancela pedidos que passaram do prazo sem pagamento, devolvendo o estoque.
 *
 * <p>Sem isto, cada pedido abandonado — e a maioria dos carrinhos e abandonada —
 * ficaria segurando unidades para sempre. O catalogo mostraria "esgotado" com o
 * deposito cheio, e a loja negaria venda a quem compraria agora por causa de
 * quem desistiu ontem.</p>
 *
 * <p>Reaproveita {@link CanceladorDePedido}: expirar <b>e</b> cancelar, so que
 * decidido pelo relogio em vez de por uma pessoa. Duplicar a logica de
 * devolucao de estoque aqui criaria duas implementacoes da mesma regra — e a
 * segunda envelheceria sozinha.</p>
 */
@Component
public class ExpiradorDePedidos {

    private static final Logger log = LoggerFactory.getLogger(ExpiradorDePedidos.class);

    private final PedidoRepositorio pedidos;
    private final CanceladorDePedido cancelador;
    private final PropriedadesDeExpiracao propriedades;
    private final Metricas metricas;
    private final Clock relogio;

    ExpiradorDePedidos(PedidoRepositorio pedidos, CanceladorDePedido cancelador,
                       PropriedadesDeExpiracao propriedades, Metricas metricas, Clock relogio) {
        this.pedidos = pedidos;
        this.cancelador = cancelador;
        this.propriedades = propriedades;
        this.metricas = metricas;
        this.relogio = relogio;
    }

    @Scheduled(fixedDelayString = "${pedidos.expiracao.intervalo-ms:60000}")
    public void rodar() {
        Correlacao.executarCom(Correlacao.gerar(), this::expirarVencidos);
    }

    /**
     * Expira os vencidos e devolve quantos foram cancelados.
     *
     * <p>Nao e transacional: cada pedido e cancelado em transacao propria, dentro
     * do {@link CanceladorDePedido}. Um lote inteiro numa transacao faria a
     * falha de um pedido desfazer o cancelamento de todos os outros.</p>
     *
     * <p>Publico para que os testes chamem direto, sem depender do agendador.</p>
     */
    public int expirarVencidos() {
        Instant limite = relogio.instant().minus(propriedades.prazo());
        List<Long> vencidos = pedidos.idsAguardandoPagamentoDesdeAntesDe(limite, propriedades.tamanhoDoLote());

        if (vencidos.isEmpty()) {
            return 0;
        }

        int expirados = 0;
        for (Long id : vencidos) {
            try {
                cancelador.cancelar(id);
                expirados++;
            } catch (RuntimeException e) {
                // O caso esperado: o cliente pagou entre a consulta e o
                // cancelamento, e a entidade recusou a transicao. Nao e erro —
                // e a maquina de estados fazendo o seu trabalho. O pedido segue
                // pago, e a proxima rodada nem o encontra.
                log.info("Pedido {} nao foi expirado: {}", id, e.getMessage());
            }
        }

        if (expirados > 0) {
            metricas.pedidosExpirados(expirados);
            log.info("{} pedido(s) expirado(s) por falta de pagamento; estoque devolvido.", expirados);
        }
        return expirados;
    }
}
