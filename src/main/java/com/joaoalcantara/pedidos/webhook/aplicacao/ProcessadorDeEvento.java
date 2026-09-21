package com.joaoalcantara.pedidos.webhook.aplicacao;

import java.time.Clock;
import java.util.Comparator;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.joaoalcantara.pedidos.outbox.aplicacao.RegistradorDeEvento;
import com.joaoalcantara.pedidos.outbox.dominio.EventosDePedido;
import com.joaoalcantara.pedidos.outbox.dominio.PedidoPago;
import com.joaoalcantara.pedidos.pagamento.dominio.Pagamento;
import com.joaoalcantara.pedidos.pagamento.dominio.PagamentoRepositorio;
import com.joaoalcantara.pedidos.pagamento.dominio.StatusPagamento;
import com.joaoalcantara.pedidos.pedido.dominio.ItemPedido;
import com.joaoalcantara.pedidos.pedido.dominio.Pedido;
import com.joaoalcantara.pedidos.pedido.dominio.PedidoRepositorio;
import com.joaoalcantara.pedidos.produto.dominio.Produto;
import com.joaoalcantara.pedidos.produto.dominio.ProdutoRepositorio;
import com.joaoalcantara.pedidos.webhook.dominio.EventoProcessado;
import com.joaoalcantara.pedidos.webhook.dominio.EventoProcessadoRepositorio;

/**
 * Aplica o efeito de um evento do gateway, <b>numa unica transacao</b>.
 *
 * <p>A primeira linha do metodo grava o identificador do evento numa tabela com
 * indice unico. A ultima linha aplica o efeito. As duas estao na mesma
 * transacao, e e so isso que torna o processamento idempotente:</p>
 *
 * <ul>
 *   <li>Se o efeito falhar, o registro do evento some junto — e a proxima
 *       entrega tenta de novo, como deve.</li>
 *   <li>Se o evento ja tiver sido processado, o {@code INSERT} colide no indice
 *       unico e a transacao inteira e desfeita — sem o efeito acontecer duas
 *       vezes.</li>
 * </ul>
 *
 * <p>A alternativa comum — consultar "ja processei este evento?" antes de
 * aplicar — tem uma janela entre a consulta e a gravacao. Duas entregas
 * simultaneas passam as duas pela consulta e o estoque sai em dobro. A consulta
 * nao e inutil (ela evita provocar erro no banco no caso comum), mas nao
 * substitui a restricao.</p>
 */
@Component
class ProcessadorDeEvento {

    private static final Logger log = LoggerFactory.getLogger(ProcessadorDeEvento.class);

    private final EventoProcessadoRepositorio eventos;
    private final PagamentoRepositorio pagamentos;
    private final PedidoRepositorio pedidos;
    private final ProdutoRepositorio produtos;
    private final RegistradorDeEvento registrador;
    private final Clock relogio;

    ProcessadorDeEvento(EventoProcessadoRepositorio eventos, PagamentoRepositorio pagamentos,
                        PedidoRepositorio pedidos, ProdutoRepositorio produtos,
                        RegistradorDeEvento registrador, Clock relogio) {
        this.eventos = eventos;
        this.pagamentos = pagamentos;
        this.pedidos = pedidos;
        this.produtos = produtos;
        this.registrador = registrador;
        this.relogio = relogio;
    }

    @Transactional
    void processar(EventoDoGateway evento) {
        // Primeiro o registro: se esta linha colidir, nada do que vem abaixo
        // chega a acontecer.
        eventos.salvar(new EventoProcessado(evento.id(), evento.tipo(), relogio.instant()));

        switch (evento.tipo()) {
            case EventoDoGateway.PAGAMENTO_APROVADO -> aprovar(evento);
            case EventoDoGateway.PAGAMENTO_RECUSADO -> registrarStatus(evento, StatusPagamento.RECUSADO);
            case EventoDoGateway.PAGAMENTO_CANCELADO -> registrarStatus(evento, StatusPagamento.CANCELADO);
            default -> log.debug("Evento '{}' recebido e ignorado: tipo sem tratamento.", evento.tipo());
        }
    }

    /**
     * Pagamento aprovado: o pedido vai para PAGO e as unidades reservadas saem
     * definitivamente do estoque.
     *
     * <p>Tudo aqui dentro da mesma transacao do registro do evento. Um webhook
     * entregue duas vezes nao consegue baixar o estoque duas vezes, porque a
     * segunda transacao inteira e descartada.</p>
     */
    private void aprovar(EventoDoGateway evento) {
        Pagamento pagamento = localizar(evento);
        pagamento.registrarStatus(StatusPagamento.APROVADO, relogio.instant());
        pagamentos.salvar(pagamento);

        Pedido pedido = pedidos.porIdComTrava(pagamento.getPedidoId()).orElseThrow();

        // A entidade recusa a transicao se o pedido nao estiver aguardando
        // pagamento — um pedido cancelado que recebe "aprovado" atrasado para
        // aqui, em vez de ressuscitar.
        pedido.marcarComoPago();
        pedidos.salvar(pedido);

        // A baixa do estoque NAO acontece aqui. Ela saiu para o consumidor da
        // fila (Etapa 9): no webhook, ela fazia a confirmacao do pagamento
        // depender de o estoque estar saudavel, e um problema de estoque
        // derrubaria a transacao inteira — com o gateway reenviando o evento por
        // horas por causa de algo que nao tem relacao com o pagamento.

        // Mesma transacao do fato: ou o pedido fica PAGO e o evento existe, ou
        // nenhum dos dois. Nao ha publicacao aqui — publicar dentro da transacao
        // e o erro que o outbox resolve.
        registrador.registrar(EventosDePedido.PEDIDO_PAGO, String.valueOf(pedido.getId()),
                new PedidoPago(pedido.getId(), pedido.getUsuarioId(), pedido.getValorTotal(),
                        pagamento.getIdExterno(), relogio.instant()));
    }

    private void registrarStatus(EventoDoGateway evento, StatusPagamento status) {
        Pagamento pagamento = localizar(evento);
        pagamento.registrarStatus(status, relogio.instant());
        pagamentos.salvar(pagamento);
        // O pedido continua AGUARDANDO_PAGAMENTO: uma tentativa recusada nao
        // encerra o pedido, o cliente ainda pode pagar com outro cartao. Quem
        // encerra pedido nao pago e a expiracao, na Etapa 10.
    }

    private Pagamento localizar(EventoDoGateway evento) {
        return pagamentos.porIdExterno(evento.idDaCobranca())
                .orElseThrow(() -> new CobrancaDesconhecidaException(evento.idDaCobranca()));
    }
}
