package com.joaoalcantara.pedidos.seguranca;

import java.io.IOException;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/** Responde 401 em Problem Details quando falta autenticacao valida. */
@Component
public class PontoDeEntradaNaoAutorizado implements AuthenticationEntryPoint {

    private final EscritorDeProblema escritor;

    public PontoDeEntradaNaoAutorizado(EscritorDeProblema escritor) {
        this.escritor = escritor;
    }

    @Override
    public void commence(HttpServletRequest requisicao, HttpServletResponse resposta,
                         AuthenticationException excecao) throws IOException {
        escritor.escrever(resposta, HttpStatus.UNAUTHORIZED, "nao-autenticado",
                "Nao autenticado", "Envie um token valido no header Authorization");
    }
}
