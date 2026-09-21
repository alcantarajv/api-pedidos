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

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/auth")
public class AutenticacaoController {

    private final AutenticacaoServico servico;

    public AutenticacaoController(AutenticacaoServico servico) {
        this.servico = servico;
    }

    @PostMapping("/registrar")
    @ResponseStatus(HttpStatus.CREATED)
    public UsuarioResposta registrar(@Valid @RequestBody RegistroRequisicao requisicao) {
        return UsuarioResposta.de(servico.registrar(requisicao));
    }

    @PostMapping("/login")
    public TokenResposta login(@Valid @RequestBody LoginRequisicao requisicao) {
        ServicoDeToken.TokenEmitido emitido = servico.autenticar(requisicao);
        return TokenResposta.bearer(emitido.token(), emitido.expiraEm(), emitido.expiraEmSegundos());
    }

    /** Util para o cliente confirmar quem o token representa. */
    @GetMapping("/eu")
    public UsuarioResposta eu(@AuthenticationPrincipal UsuarioAutenticado autenticado) {
        return UsuarioResposta.de(servico.buscarPorId(autenticado.id()));
    }
}
