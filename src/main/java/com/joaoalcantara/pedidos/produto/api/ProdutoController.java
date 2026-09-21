package com.joaoalcantara.pedidos.produto.api;

import java.net.URI;
import java.util.List;

import org.springframework.http.ResponseEntity;
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

import jakarta.validation.Valid;

/**
 * Endpoints do catalogo.
 *
 * <p><b>Atencao:</b> nesta etapa a escrita ainda esta aberta — o Spring Security
 * so entra na Etapa 3, quando leitura vira publica e escrita passa a exigir
 * ADMIN. A autorizacao sera declarada por padrao de rota na configuracao de
 * seguranca, para ficar num lugar so e ser auditavel de relance.</p>
 */
@RestController
@RequestMapping("/api/produtos")
public class ProdutoController {

    private final ProdutoServico servico;

    public ProdutoController(ProdutoServico servico) {
        this.servico = servico;
    }

    @GetMapping
    public List<ProdutoResposta> listar(@RequestParam(defaultValue = "true") boolean apenasAtivos) {
        return servico.listar(apenasAtivos).stream().map(ProdutoResposta::de).toList();
    }

    @GetMapping("/{id}")
    public ProdutoResposta buscar(@PathVariable Long id) {
        return ProdutoResposta.de(servico.buscar(id));
    }

    @PostMapping
    public ResponseEntity<ProdutoResposta> criar(@Valid @RequestBody ProdutoRequisicao requisicao) {
        Produto produto = servico.criar(requisicao);
        return ResponseEntity
                .created(URI.create("/api/produtos/" + produto.getId()))
                .body(ProdutoResposta.de(produto));
    }

    @PutMapping("/{id}")
    public ProdutoResposta atualizar(@PathVariable Long id, @Valid @RequestBody ProdutoRequisicao requisicao) {
        return ProdutoResposta.de(servico.atualizar(id, requisicao));
    }

    /**
     * Ajuste de estoque em endpoint proprio, e nao como campo do PUT: entrada de
     * mercadoria e correcao de cadastro sao operacoes diferentes, feitas em
     * momentos diferentes e — quando houver autorizacao — possivelmente por
     * pessoas diferentes.
     */
    @PutMapping("/{id}/estoque")
    public ProdutoResposta ajustarEstoque(@PathVariable Long id, @Valid @RequestBody EstoqueRequisicao requisicao) {
        return ProdutoResposta.de(servico.ajustarEstoque(id, requisicao.estoqueDisponivel()));
    }

    /**
     * Ativacao/desativacao tambem e endpoint proprio: e uma transicao de estado
     * com significado de negocio (parar de aceitar pedidos novos), nao a edicao
     * de um atributo qualquer.
     */
    @PutMapping("/{id}/situacao")
    public ProdutoResposta alterarSituacao(@PathVariable Long id, @Valid @RequestBody SituacaoRequisicao requisicao) {
        return ProdutoResposta.de(servico.alterarSituacao(id, requisicao.ativo()));
    }
}
