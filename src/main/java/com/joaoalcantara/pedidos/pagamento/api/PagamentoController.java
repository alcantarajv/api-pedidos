package com.joaoalcantara.pedidos.pagamento.api;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.joaoalcantara.pedidos.pagamento.aplicacao.PagamentoServico;
import com.joaoalcantara.pedidos.seguranca.UsuarioAutenticado;

/**
 * Cobranca de um pedido.
 *
 * <p>Fica sob {@code /api/pedidos/{id}/pagamento} porque a cobranca nao existe
 * sozinha: ela e sempre a cobranca de um pedido. A autorizacao vem de graca
 * dessa escolha — quem pode ver o pedido pode ver a cobranca dele, e a checagem
 * e a mesma do {@code PedidoServico}.</p>
 */
@RestController
@RequestMapping("/api/pedidos/{pedidoId}/pagamento")
public class PagamentoController {

    private final PagamentoServico servico;

    public PagamentoController(PagamentoServico servico) {
        this.servico = servico;
    }

    /**
     * Cria a cobranca no gateway.
     *
     * <p>Responde 201 sempre que ha uma cobranca ativa para o pedido, inclusive
     * quando ela ja existia: chamar duas vezes nao cria duas cobrancas.</p>
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PagamentoResposta criar(@PathVariable Long pedidoId,
                                   @AuthenticationPrincipal UsuarioAutenticado autenticado) {
        var criada = servico.criarCobranca(autenticado, pedidoId);
        return PagamentoResposta.comSegredo(criada.pagamento(), criada.segredoDoCliente());
    }

    @GetMapping
    public PagamentoResposta consultar(@PathVariable Long pedidoId,
                                       @AuthenticationPrincipal UsuarioAutenticado autenticado) {
        return PagamentoResposta.de(servico.consultar(autenticado, pedidoId));
    }
}
