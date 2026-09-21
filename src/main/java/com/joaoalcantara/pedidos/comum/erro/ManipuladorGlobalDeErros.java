package com.joaoalcantara.pedidos.comum.erro;

import java.net.URI;
import java.time.Instant;
import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Traduz excecoes em respostas no formato Problem Details (RFC 9457).
 *
 * <p>A API nunca devolve stack trace nem mensagem de driver de banco: cada
 * excecao de dominio vira um documento {@code application/problem+json} com um
 * {@code type} estavel, que o cliente pode tratar programaticamente.</p>
 */
@RestControllerAdvice
public class ManipuladorGlobalDeErros extends ResponseEntityExceptionHandler {

    private static final String BASE_TIPO = "https://api.pedidos.dev/erros/";

    @ExceptionHandler(RecursoNaoEncontradoException.class)
    public ProblemDetail trataNaoEncontrado(RecursoNaoEncontradoException e) {
        return problema(HttpStatus.NOT_FOUND, "recurso-nao-encontrado", "Recurso nao encontrado", e.getMessage());
    }

    @ExceptionHandler(RegraDeNegocioException.class)
    public ProblemDetail trataRegraDeNegocio(RegraDeNegocioException e) {
        return problema(HttpStatus.UNPROCESSABLE_ENTITY, e.codigo(), "Regra de negocio violada", e.getMessage());
    }

    @ExceptionHandler(ConflitoException.class)
    public ProblemDetail trataConflito(ConflitoException e) {
        return problema(HttpStatus.CONFLICT, e.codigo(), "Conflito", e.getMessage());
    }

    /**
     * Login com e-mail ou senha errados: 401, sem revelar qual dos dois falhou.
     *
     * <p>A mensagem e fixa de proposito. "Usuario nao encontrado" contra "senha
     * incorreta" transformaria a tela de login num verificador de quais e-mails
     * tem conta aqui.</p>
     */
    @ExceptionHandler(BadCredentialsException.class)
    public ProblemDetail trataCredenciaisInvalidas(BadCredentialsException e) {
        return problema(HttpStatus.UNAUTHORIZED, "credenciais-invalidas",
                "Credenciais invalidas", "E-mail ou senha incorretos");
    }

    /** Falhas de Bean Validation nos DTOs de entrada: 400 com a lista de campos. */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException e, HttpHeaders headers, HttpStatusCode status, WebRequest request) {

        List<String> erros = e.getBindingResult().getFieldErrors().stream()
                .map(erro -> "%s: %s".formatted(erro.getField(), erro.getDefaultMessage()))
                .sorted()
                .toList();

        ProblemDetail corpo = problema(HttpStatus.BAD_REQUEST, "requisicao-invalida",
                "Requisicao invalida", "Um ou mais campos estao invalidos");
        corpo.setProperty("erros", erros);
        return ResponseEntity.badRequest().body(corpo);
    }

    private ProblemDetail problema(HttpStatus status, String codigo, String titulo, String detalhe) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(status, detalhe);
        problema.setType(URI.create(BASE_TIPO + codigo));
        problema.setTitle(titulo);
        problema.setProperty("ocorridoEm", Instant.now());
        return problema;
    }
}
