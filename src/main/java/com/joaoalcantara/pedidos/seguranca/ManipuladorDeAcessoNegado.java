package com.joaoalcantara.pedidos.seguranca;

import java.io.IOException;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/** Responde 403 em Problem Details quando o usuario esta autenticado mas nao tem o papel. */
@Component
public class ManipuladorDeAcessoNegado implements AccessDeniedHandler {

    private final EscritorDeProblema escritor;

    public ManipuladorDeAcessoNegado(EscritorDeProblema escritor) {
        this.escritor = escritor;
    }

    @Override
    public void handle(HttpServletRequest requisicao, HttpServletResponse resposta,
                       AccessDeniedException excecao) throws IOException {
        escritor.escrever(resposta, HttpStatus.FORBIDDEN, "acesso-negado",
                "Acesso negado", "Seu usuario nao tem permissao para esta operacao");
    }
}
