package com.joaoalcantara.pedidos.usuario.api;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.joaoalcantara.pedidos.seguranca.ServicoDeToken;
import com.joaoalcantara.pedidos.seguranca.UsuarioAutenticado;
import com.joaoalcantara.pedidos.usuario.aplicacao.AutenticacaoServico;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@Tag(name = "Autenticacao", description = "Registro, login e identificacao do usuario")
@RestController
@RequestMapping("/api/auth")
public class AutenticacaoController {

    private final AutenticacaoServico servico;

    public AutenticacaoController(AutenticacaoServico servico) {
        this.servico = servico;
    }

    @Operation(summary = "Registra um novo cliente",
            description = "O registro publico cria sempre um usuario com papel CLIENTE. Promover "
                    + "alguem a ADMIN e operacao administrativa deliberada: se o papel viesse no "
                    + "corpo da requisicao, qualquer um se autopromoveria.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Conta criada"),
            @ApiResponse(responseCode = "400", description = "Campos invalidos", content = @Content),
            @ApiResponse(responseCode = "409", description = "E-mail ja cadastrado", content = @Content)
    })
    @SecurityRequirements
    @PostMapping("/registrar")
    @ResponseStatus(HttpStatus.CREATED)
    public UsuarioResposta registrar(@Valid @RequestBody RegistroRequisicao requisicao) {
        return UsuarioResposta.de(servico.registrar(requisicao));
    }

    @Operation(summary = "Autentica e devolve o token JWT",
            description = "E-mail inexistente e senha errada produzem exatamente o mesmo erro: "
                    + "distinguir os dois permitiria descobrir quais e-mails tem conta aqui.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Token emitido"),
            @ApiResponse(responseCode = "401", description = "Credenciais invalidas", content = @Content)
    })
    @SecurityRequirements
    @PostMapping("/login")
    public TokenResposta login(@Valid @RequestBody LoginRequisicao requisicao) {
        ServicoDeToken.TokenEmitido emitido = servico.autenticar(requisicao);
        return TokenResposta.bearer(emitido.token(), emitido.expiraEm(), emitido.expiraEmSegundos());
    }

    /** Util para o cliente confirmar quem o token representa. */
    @Operation(summary = "Devolve o usuario dono do token")
    @GetMapping("/eu")
    public UsuarioResposta eu(@AuthenticationPrincipal UsuarioAutenticado autenticado) {
        return UsuarioResposta.de(servico.buscarPorId(autenticado.id()));
    }
}
