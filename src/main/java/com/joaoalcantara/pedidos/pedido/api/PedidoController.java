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

import jakarta.validation.Valid;

/**
 * Endpoints de pedidos. Exigem autenticacao: um CLIENTE ve e cria os proprios,
 * um ADMIN enxerga todos.
 */
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
    @PostMapping
    public ResponseEntity<PedidoResposta> criar(
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

    @GetMapping
    public List<PedidoResposta> listar(@AuthenticationPrincipal UsuarioAutenticado autenticado) {
        return servico.listar(autenticado).stream().map(PedidoResposta::resumida).toList();
    }

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
