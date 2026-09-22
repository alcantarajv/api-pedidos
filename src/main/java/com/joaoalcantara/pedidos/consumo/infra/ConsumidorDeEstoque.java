package com.joaoalcantara.pedidos.consumo.infra;

import java.util.Comparator;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.joaoalcantara.pedidos.comum.observabilidade.Correlacao;
import com.joaoalcantara.pedidos.consumo.aplicacao.ConsumoIdempotente;
import com.joaoalcantara.pedidos.outbox.dominio.PedidoPago;
import com.joaoalcantara.pedidos.outbox.infra.FilaConfig;
import com.joaoalcantara.pedidos.pedido.dominio.ItemPedido;
import com.joaoalcantara.pedidos.pedido.dominio.Pedido;
import com.joaoalcantara.pedidos.pedido.dominio.PedidoRepositorio;
import com.joaoalcantara.pedidos.produto.dominio.Produto;
import com.joaoalcantara.pedidos.produto.dominio.ProdutoRepositorio;

import tools.jackson.databind.ObjectMapper;

/**
 * Baixa definitivamente o estoque quando um pedido e pago.
 *
 * <p>Esta baixa saiu do processamento do webhook e veio para ca de proposito. No
 * webhook, ela fazia a confirmacao do pagamento depender de o estoque estar
 * saudavel: um produto com dados inconsistentes derrubaria a transacao e o
 * gateway receberia erro, reenviando o evento por horas — por causa de um
 * problema que nao tem nada a ver com o pagamento.</p>
 *
 * <p>Separados, cada um falha sozinho. O pagamento e confirmado, o evento fica
 * guardado no outbox, e a baixa de estoque e reprocessada pela fila quantas
 * vezes for preciso.</p>
 */
@Component
public class ConsumidorDeEstoque {

    private static final Logger log = LoggerFactory.getLogger(ConsumidorDeEstoque.class);

    /** Nome usado na chave de idempotencia. Mudar isto reprocessa tudo. */
    private static final String NOME = "estoque";

    private final ConsumoIdempotente consumo;
    private final BaixaDeEstoque baixa;
    private final ObjectMapper objectMapper;

    public ConsumidorDeEstoque(ConsumoIdempotente consumo, BaixaDeEstoque baixa, ObjectMapper objectMapper) {
        this.consumo = consumo;
        this.baixa = baixa;
        this.objectMapper = objectMapper;
    }

    @RabbitListener(queues = FilaConfig.FILA_ESTOQUE)
    public void receber(@Payload String corpo,
                        @Header(name = "x-id-evento", required = false) String idEvento,
                        @Header(name = Correlacao.CABECALHO_AMQP, required = false) String correlacaoId) {

        Correlacao.executarCom(correlacaoId, () -> processar(corpo, idEvento));
    }

    private void processar(String corpo, String idEvento) {

        if (idEvento == null || idEvento.isBlank()) {
            // Sem chave de idempotencia nao ha como garantir efeito unico.
            // Rejeitar e melhor do que processar as cegas: a mensagem vai para a
            // fila de mortas e alguem descobre o publicador defeituoso.
            throw new IllegalArgumentException("Mensagem sem o cabecalho x-id-evento");
        }

        PedidoPago evento = objectMapper.readValue(corpo, PedidoPago.class);

        boolean aplicou = consumo.executar(idEvento, NOME, () -> baixa.executar(evento.pedidoId()));

        if (aplicou) {
            log.info("Estoque baixado para o pedido {}.", evento.pedidoId());
        }
    }

    /** O efeito, em bean proprio para que o {@code @Transactional} valha. */
    @Component
    public static class BaixaDeEstoque {

        private final PedidoRepositorio pedidos;
        private final ProdutoRepositorio produtos;

        public BaixaDeEstoque(PedidoRepositorio pedidos, ProdutoRepositorio produtos) {
            this.pedidos = pedidos;
            this.produtos = produtos;
        }

        /**
         * Participa da transacao aberta pelo aplicador de consumo — por isso
         * {@code MANDATORY}: se algum dia for chamado fora dela, falha na hora
         * em vez de gravar sem a protecao da idempotencia.
         */
        @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
        public void executar(Long pedidoId) {
            Pedido pedido = pedidos.porIdComItens(pedidoId).orElseThrow();

            // Mesma ordem de travamento do resto do sistema: evita deadlock.
            List<ItemPedido> itens = pedido.getItens().stream()
                    .sorted(Comparator.comparing(ItemPedido::getProdutoId))
                    .toList();

            for (ItemPedido item : itens) {
                Produto produto = produtos.porIdComTrava(item.getProdutoId()).orElseThrow();
                produto.confirmarVenda(item.getQuantidade());
                produtos.salvar(produto);
            }
        }
    }
}
