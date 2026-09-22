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

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Cobranca de um pedido.
 *
 * <p>Fica sob {@code /api/pedidos/{id}/pagamento} porque a cobranca nao existe
 * sozinha: ela e sempre a cobranca de um pedido. A autorizacao vem de graca
 * dessa escolha — quem pode ver o pedido pode ver a cobranca dele, e a checagem
 * e a mesma do {@code PedidoServico}.</p>
 */
@Tag(name = "Pagamentos", description = "Cobranca de um pedido no gateway externo")
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
    @Operation(summary = "Cria a cobranca no gateway",
            description = """
                    Devolve o segredo que o front-end usa para concluir o pagamento.

                    Chamar duas vezes NAO cria duas cobrancas: ha tres protecoes em camadas — a
                    consulta previa, o indice unico por pedido e a chave de idempotencia enviada ao
                    proprio gateway. A ultima e a unica que cobre o caso em que a nossa requisicao
                    chegou la e a resposta se perdeu no caminho de volta.

                    **A cobranca criada aqui nao confirma o pedido.** Quem leva o pedido a PAGO e
                    exclusivamente o webhook do gateway.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Cobranca criada, ou a que ja existia"),
            @ApiResponse(responseCode = "404", description = "Pedido inexistente, ou de outro cliente", content = @Content),
            @ApiResponse(responseCode = "422", description = "O pedido nao esta em estado cobravel", content = @Content),
            @ApiResponse(responseCode = "502", description = "O gateway nao respondeu ou recusou a requisicao", content = @Content)
    })
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PagamentoResposta criar(@PathVariable Long pedidoId,
                                   @AuthenticationPrincipal UsuarioAutenticado autenticado) {
        var criada = servico.criarCobranca(autenticado, pedidoId);
        return PagamentoResposta.comSegredo(criada.pagamento(), criada.segredoDoCliente());
    }

    @Operation(summary = "Consulta o estado da cobranca",
            description = "Le o gateway e atualiza o registro local. NAO confirma o pedido, mesmo "
                    + "que o gateway diga aprovado: essa transicao pertence so ao webhook. Dois "
                    + "caminhos capazes de confirmar um pedido seriam duas implementacoes da mesma "
                    + "regra, e a segunda e a que confirma duas vezes.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Estado atual da cobranca"),
            @ApiResponse(responseCode = "404", description = "Pedido ou cobranca inexistente", content = @Content),
            @ApiResponse(responseCode = "502", description = "O gateway nao respondeu", content = @Content)
    })
    @GetMapping
    public PagamentoResposta consultar(@PathVariable Long pedidoId,
                                       @AuthenticationPrincipal UsuarioAutenticado autenticado) {
        return PagamentoResposta.de(servico.consultar(autenticado, pedidoId));
    }
}
