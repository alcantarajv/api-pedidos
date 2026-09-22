package com.joaoalcantara.pedidos.produto.api;

import java.net.URI;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.joaoalcantara.pedidos.produto.aplicacao.ProdutoServico;
import com.joaoalcantara.pedidos.produto.dominio.Produto;
import com.joaoalcantara.pedidos.seguranca.UsuarioAutenticado;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/**
 * Endpoints do catalogo.
 *
 * <p>Leitura e publica; escrita exige ADMIN. A autorizacao propriamente dita e
 * declarada por padrao de rota em {@code SegurancaConfig}, num lugar so — o
 * controller nao repete essa decisao.</p>
 *
 * <p>Nas leituras o papel muda o <b>conteudo</b> da resposta, e nao o acesso:
 * quem nao for ADMIN recebe a visao publica, sem as parcelas internas do
 * estoque. Por isso o principal e injetado aqui, ainda que a rota seja aberta —
 * ele vem nulo para visitante anonimo.</p>
 */
@Tag(name = "Catalogo", description = "Consulta publica e gestao administrativa de produtos")
@RestController
@RequestMapping("/api/produtos")
public class ProdutoController {

    private final ProdutoServico servico;

    public ProdutoController(ProdutoServico servico) {
        this.servico = servico;
    }

    @Operation(summary = "Lista produtos",
            description = "Publico. Com token de ADMIN a resposta traz tambem as parcelas internas "
                    + "do estoque (reservado e total); sem ele, so o disponivel.")
    @SecurityRequirements
    @GetMapping
    public List<ProdutoVisao> listar(@RequestParam(defaultValue = "true") boolean apenasAtivos,
                                     @AuthenticationPrincipal UsuarioAutenticado autenticado) {
        return servico.listar(apenasAtivos).stream()
                .map(produto -> visaoPara(produto, autenticado))
                .toList();
    }

    @Operation(summary = "Busca um produto", description = "Publico.")
    @SecurityRequirements
    @GetMapping("/{id}")
    public ProdutoVisao buscar(@PathVariable Long id,
                               @AuthenticationPrincipal UsuarioAutenticado autenticado) {
        return visaoPara(servico.buscar(id), autenticado);
    }

    @Operation(summary = "Cria um produto", description = "Exige papel ADMIN.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Produto criado"),
            @ApiResponse(responseCode = "409", description = "Ja existe produto com esse nome", content = @Content)
    })
    @PostMapping
    public ResponseEntity<ProdutoAdminResposta> criar(@Valid @RequestBody ProdutoRequisicao requisicao) {
        Produto produto = servico.criar(requisicao);
        return ResponseEntity
                .created(URI.create("/api/produtos/" + produto.getId()))
                .body(ProdutoAdminResposta.de(produto));
    }

    @Operation(summary = "Atualiza nome, descricao e preco",
            description = "Exige ADMIN. NAO altera estoque nem pedidos existentes: o preco de cada "
                    + "item fica congelado no momento da compra.")
    @PutMapping("/{id}")
    public ProdutoAdminResposta atualizar(@PathVariable Long id, @Valid @RequestBody ProdutoRequisicao requisicao) {
        return ProdutoAdminResposta.de(servico.atualizar(id, requisicao));
    }

    /**
     * Ajuste de estoque em endpoint proprio, e nao como campo do PUT: entrada de
     * mercadoria e correcao de cadastro sao operacoes diferentes, feitas em
     * momentos diferentes e possivelmente por pessoas diferentes.
     */
    @Operation(summary = "Ajusta o estoque disponivel",
            description = """
                    Exige ADMIN. O corpo traz a quantidade FINAL, nao um incremento: reenviar a mesma
                    requisicao leva ao mesmo estado, em vez de somar de novo.

                    So mexe no disponivel. O reservado pertence a pedidos de clientes reais e so muda
                    pelo ciclo de vida do pedido.
                    """)
    @PutMapping("/{id}/estoque")
    public ProdutoAdminResposta ajustarEstoque(@PathVariable Long id, @Valid @RequestBody EstoqueRequisicao requisicao) {
        return ProdutoAdminResposta.de(servico.ajustarEstoque(id, requisicao.estoqueDisponivel()));
    }

    /**
     * Ativacao/desativacao tambem e endpoint proprio: e uma transicao de estado
     * com significado de negocio (parar de aceitar pedidos novos), nao a edicao
     * de um atributo qualquer.
     */
    @Operation(summary = "Ativa ou desativa o produto",
            description = "Exige ADMIN. Desativar impede pedidos NOVOS; os que ja existem continuam "
                    + "valendo, e o estoque reservado por eles nao e devolvido.")
    @PutMapping("/{id}/situacao")
    public ProdutoAdminResposta alterarSituacao(@PathVariable Long id, @Valid @RequestBody SituacaoRequisicao requisicao) {
        return ProdutoAdminResposta.de(servico.alterarSituacao(id, requisicao.ativo()));
    }

    private ProdutoVisao visaoPara(Produto produto, UsuarioAutenticado autenticado) {
        boolean admin = autenticado != null && autenticado.ehAdmin();
        return admin ? ProdutoAdminResposta.de(produto) : ProdutoResposta.de(produto);
    }
}
