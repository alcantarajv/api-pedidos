package com.joaoalcantara.pedidos.pedido.api;

import java.net.URI;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.joaoalcantara.pedidos.comum.erro.RegraDeNegocioException;
import com.joaoalcantara.pedidos.pedido.aplicacao.PedidoServico;
import com.joaoalcantara.pedidos.seguranca.UsuarioAutenticado;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/**
 * Endpoints de pedidos. Exigem autenticacao: um CLIENTE ve e cria os proprios,
 * um ADMIN enxerga todos.
 */
@Tag(name = "Pedidos", description = "Criacao, consulta e cancelamento de pedidos")
@RestController
@RequestMapping("/api/pedidos")
public class PedidoController {

    private static final int TAMANHO_MAXIMO_DA_CHAVE = 80;

    private final PedidoServico servico;

    public PedidoController(PedidoServico servico) {
        this.servico = servico;
    }

    /**
     * Cria um pedido.
     *
     * <p>O cabecalho {@code Idempotency-Key} e <b>obrigatorio</b>. Poderia ser
     * opcional, como no Stripe, mas criar pedido e a operacao mais cara de se
     * duplicar neste sistema: o cliente fica com duas cobrancas e o estoque sai
     * em dobro. Exigir a chave transfere para o cliente uma decisao trivial
     * (gerar um UUID) e elimina a classe inteira de problemas.</p>
     *
     * <p>Responde 201 quando o pedido nasce agora e 200 quando a chave ja havia
     * sido usada — o corpo e identico, e o status conta o que aconteceu.</p>
     */
    @Operation(summary = "Cria um pedido",
            description = """
                    Reserva o estoque de cada item e congela o preco praticado no momento da compra.

                    O cabecalho `Idempotency-Key` e **obrigatorio** e deve ser unico por tentativa \
                    (um UUID serve). Reenviar a mesma chave devolve o pedido ja criado com **200**, \
                    em vez de criar um segundo com 201 — e o que torna seguro repetir a requisicao \
                    depois de um timeout.

                    A chave e unica por usuario: dois clientes podem usar a mesma string sem \
                    interferencia.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Pedido criado"),
            @ApiResponse(responseCode = "200", description = "A chave ja havia sido usada: devolve o pedido anterior"),
            @ApiResponse(responseCode = "400", description = "Corpo invalido (sem itens, quantidade menor que 1)", content = @Content),
            @ApiResponse(responseCode = "409", description = "Estoque insuficiente para algum item", content = @Content),
            @ApiResponse(responseCode = "422", description = "Cabecalho Idempotency-Key ausente, produto inativo ou item repetido", content = @Content)
    })
    @PostMapping
    public ResponseEntity<PedidoResposta> criar(
            @Parameter(description = "Identificador unico desta tentativa de pedido. Use um UUID.",
                    required = true, example = "7c9e6679-7425-40de-944b-e07fc1f90ae7")
            @RequestHeader(name = "Idempotency-Key", required = false) String chaveIdempotencia,
            @Valid @RequestBody PedidoRequisicao requisicao,
            @AuthenticationPrincipal UsuarioAutenticado autenticado) {

        String chave = validarChave(chaveIdempotencia);
        var resultado = servico.criar(autenticado, chave, requisicao);
        PedidoResposta corpo = PedidoResposta.de(resultado.pedido());

        if (!resultado.criadoAgora()) {
            return ResponseEntity.ok(corpo);
        }
        return ResponseEntity
                .created(URI.create("/api/pedidos/" + corpo.id()))
                .body(corpo);
    }

    @Operation(summary = "Lista pedidos",
            description = "Um CLIENTE ve apenas os proprios pedidos; um ADMIN ve todos. "
                    + "A listagem nao traz os itens — use a busca por id.")
    @GetMapping
    public List<PedidoResposta> listar(@AuthenticationPrincipal UsuarioAutenticado autenticado) {
        return servico.listar(autenticado).stream().map(PedidoResposta::resumida).toList();
    }

    @Operation(summary = "Busca um pedido com seus itens",
            description = "Pedido de outro cliente responde **404**, nao 403: responder 403 "
                    + "confirmaria que aquele pedido existe, o que ja e vazamento de informacao.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Pedido encontrado"),
            @ApiResponse(responseCode = "404", description = "Nao existe, ou e de outro cliente", content = @Content)
    })
    @GetMapping("/{id}")
    public PedidoResposta buscar(@PathVariable Long id,
                                 @AuthenticationPrincipal UsuarioAutenticado autenticado) {
        return PedidoResposta.de(servico.buscar(autenticado, id));
    }

    /**
     * Cancela o pedido e devolve o estoque reservado.
     *
     * <p>{@code POST /cancelamento} em vez de {@code DELETE /{id}}: cancelar nao
     * apaga o pedido, cria um fato novo na historia dele. O registro continua
     * existindo — e precisa continuar, porque pedido cancelado e informacao
     * contabil, nao lixo.</p>
     */
    @Operation(summary = "Cancela o pedido e devolve o estoque",
            description = """
                    Cancelar nao apaga o pedido: cria um fato novo na historia dele. Por isso e
                    POST /cancelamento, e nao DELETE — pedido cancelado e informacao contabil.

                    So pedidos que ainda aguardam pagamento podem ser cancelados. O dono do pedido
                    ou um ADMIN podem fazer isso.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Cancelado; estoque devolvido ao catalogo"),
            @ApiResponse(responseCode = "404", description = "Nao existe, ou e de outro cliente", content = @Content),
            @ApiResponse(responseCode = "422", description = "O pedido nao esta num estado que permita cancelamento", content = @Content)
    })
    @PostMapping("/{id}/cancelamento")
    public PedidoResposta cancelar(@PathVariable Long id,
                                   @AuthenticationPrincipal UsuarioAutenticado autenticado) {
        return PedidoResposta.de(servico.cancelar(autenticado, id));
    }

    /**
     * A chave e validada aqui, e nao por Bean Validation, porque cabecalho nao
     * passa pelo {@code @Valid} do corpo. Erro de cabecalho ausente vira 422 com
     * um {@code type} estavel, como qualquer outra regra.
     */
    private String validarChave(String chave) {
        if (chave == null || chave.isBlank()) {
            throw new RegraDeNegocioException("idempotency-key-ausente",
                    "Envie o cabecalho Idempotency-Key com um valor unico por tentativa de pedido");
        }
        String limpa = chave.trim();
        if (limpa.length() > TAMANHO_MAXIMO_DA_CHAVE) {
            throw new RegraDeNegocioException("idempotency-key-invalida",
                    "O cabecalho Idempotency-Key deve ter no maximo %d caracteres"
                            .formatted(TAMANHO_MAXIMO_DA_CHAVE));
        }
        return limpa;
    }
}
