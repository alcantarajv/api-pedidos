package com.joaoalcantara.pedidos.seguranca;

import java.io.IOException;
import java.net.URI;
import java.time.Instant;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;

// Spring Boot 4 usa Jackson 3: o ObjectMapper vive em tools.jackson.databind,
// nao mais em com.fasterxml.jackson.databind.
import tools.jackson.databind.ObjectMapper;

import jakarta.servlet.http.HttpServletResponse;

/**
 * Serializa Problem Details direto na resposta.
 *
 * <p>Erros de seguranca acontecem na cadeia de filtros, antes de o
 * {@code @RestControllerAdvice} entrar em cena — sem isto, um 401 sairia como
 * pagina HTML padrao do container e quebraria o contrato da API.</p>
 */
@Component
public class EscritorDeProblema {

    private static final String BASE_TIPO = "https://api.pedidos.dev/erros/";

    private final ObjectMapper objectMapper;

    public EscritorDeProblema(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void escrever(HttpServletResponse resposta, HttpStatus status, String codigo,
                         String titulo, String detalhe) throws IOException {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(status, detalhe);
        problema.setType(URI.create(BASE_TIPO + codigo));
        problema.setTitle(titulo);
        problema.setProperty("ocorridoEm", Instant.now());

        resposta.setStatus(status.value());
        resposta.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        resposta.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(resposta.getOutputStream(), problema);
    }
}
