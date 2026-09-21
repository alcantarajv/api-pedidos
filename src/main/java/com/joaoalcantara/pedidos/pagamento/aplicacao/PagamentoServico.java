package com.joaoalcantara.pedidos.pagamento.aplicacao;

import java.time.Clock;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.joaoalcantara.pedidos.comum.erro.RecursoNaoEncontradoException;
import com.joaoalcantara.pedidos.comum.erro.RegraDeNegocioException;
import com.joaoalcantara.pedidos.pagamento.dominio.GatewayDePagamento;
import com.joaoalcantara.pedidos.pagamento.dominio.Pagamento;
import com.joaoalcantara.pedidos.pagamento.dominio.PagamentoRepositorio;
import com.joaoalcantara.pedidos.pagamento.dominio.StatusPagamento;
import com.joaoalcantara.pedidos.pedido.aplicacao.PedidoServico;
import com.joaoalcantara.pedidos.pedido.dominio.Pedido;
import com.joaoalcantara.pedidos.pedido.dominio.StatusPedido;
import com.joaoalcantara.pedidos.seguranca.UsuarioAutenticado;

/** Criacao e consulta da cobranca de um pedido. */
@Service
public class PagamentoServico {

    private final PagamentoRepositorio pagamentos;
    private final PedidoServico pedidos;
    private final GatewayDePagamento gateway;
    private final Clock relogio;

    public PagamentoServico(PagamentoRepositorio pagamentos, PedidoServico pedidos,
                            GatewayDePagamento gateway, Clock relogio) {
        this.pagamentos = pagamentos;
        this.pedidos = pedidos;
        this.gateway = gateway;
        this.relogio = relogio;
    }

    /**
     * Cria — ou recupera — a cobranca do pedido.
     *
     * <p>Chamar duas vezes nao gera duas cobrancas: se ja existe pagamento para
     * o pedido, ele e devolvido como esta. Essa checagem resolve o caso comum;
     * o indice unico em {@code pedido_id} fecha a janela de duas chamadas
     * simultaneas, e a chave de idempotencia enviada ao Stripe protege o terceiro
     * cenario — o de a nossa requisicao ter chegado la e a resposta ter se
     * perdido no caminho de volta.</p>
     *
     * <p>Sao tres camadas para o mesmo problema porque elas cobrem lugares
     * diferentes: a nossa memoria, a nossa tabela e a memoria do gateway.</p>
     */
    @Transactional
    public CobrancaDoPedido criarCobranca(UsuarioAutenticado autenticado, Long pedidoId) {
        Pedido pedido = pedidos.buscar(autenticado, pedidoId);

        var existente = pagamentos.porPedido(pedidoId);
        if (existente.isPresent()) {
            Pagamento pagamento = existente.get();
            // O segredo do cliente nao e guardado no banco — e credencial de uso
            // unico, e guardar credencial que da para nao guardar e divida de
            // seguranca. Quando ele faz falta, o gateway o devolve de novo.
            var cobranca = gateway.consultar(pagamento.getIdExterno());
            return new CobrancaDoPedido(pagamento, cobranca.segredoDoCliente());
        }

        // Cobrar um pedido cancelado, ou ja pago, nao faz sentido — e cobrar um
        // cancelado devolveria dinheiro de alguem que nao comprou nada.
        if (pedido.getStatus() != StatusPedido.AGUARDANDO_PAGAMENTO) {
            throw new RegraDeNegocioException("pedido-nao-cobravel",
                    "O pedido %d esta %s e nao aceita cobranca".formatted(pedidoId, pedido.getStatus()));
        }

        // A chave do pedido e reaproveitada como chave de idempotencia no gateway:
        // ela ja e unica por pedido, e assim as duas pontas concordam sobre o que
        // e "a mesma tentativa".
        var cobranca = gateway.criar(String.valueOf(pedido.getId()), pedido.getValorTotal(),
                "cobranca-" + pedido.getChaveIdempotencia());

        Pagamento pagamento = new Pagamento(pedido, cobranca.idExterno(), pedido.getValorTotal(),
                cobranca.status(), relogio.instant());
        return new CobrancaDoPedido(pagamentos.salvar(pagamento), cobranca.segredoDoCliente());
    }

    /**
     * Le no gateway o estado atual da cobranca e atualiza o registro local.
     *
     * <p><b>Nao</b> muda o status do pedido, mesmo quando o gateway diz
     * "aprovado". Levar o pedido a PAGO e trabalho exclusivo do webhook (Etapa
     * 7), onde a idempotencia e garantida. Ter dois caminhos capazes de
     * confirmar um pedido significaria manter duas implementacoes corretas da
     * mesma regra — e a segunda, a que ninguem lembra de testar, e a que
     * confirma um pedido duas vezes.</p>
     */
    @Transactional
    public Pagamento consultar(UsuarioAutenticado autenticado, Long pedidoId) {
        pedidos.buscar(autenticado, pedidoId);

        Pagamento pagamento = pagamentos.porPedido(pedidoId)
                .orElseThrow(() -> RecursoNaoEncontradoException.de("Pagamento do pedido", pedidoId));

        if (pagamento.getStatus().ehFinal()) {
            // Estado final nao muda mais: nao ha o que perguntar ao gateway.
            return pagamento;
        }

        StatusPagamento atual = gateway.consultar(pagamento.getIdExterno()).status();
        if (atual != pagamento.getStatus()) {
            pagamento.registrarStatus(atual, relogio.instant());
            return pagamentos.salvar(pagamento);
        }
        return pagamento;
    }

    /**
     * O pagamento persistido mais o segredo efemero da vez.
     *
     * <p>Sao coisas de naturezas diferentes: uma mora no banco, a outra so faz
     * sentido nesta resposta. Guardar as duas na entidade misturaria registro
     * permanente com credencial descartavel.</p>
     */
    public record CobrancaDoPedido(Pagamento pagamento, String segredoDoCliente) {
    }
}
