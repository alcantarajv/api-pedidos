package com.joaoalcantara.pedidos.comum.erro;

import java.net.URI;
import java.time.Instant;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

import com.joaoalcantara.pedidos.webhook.dominio.AssinaturaInvalidaException;
import com.joaoalcantara.pedidos.webhook.dominio.EventoMalFormadoException;

/**
 * Traduz excecoes em respostas no formato Problem Details (RFC 9457).
 *
 * <p>A API nunca devolve stack trace nem mensagem de driver de banco: cada
 * excecao de dominio vira um documento {@code application/problem+json} com um
 * {@code type} estavel, que o cliente pode tratar programaticamente.</p>
 */
@RestControllerAdvice
public class ManipuladorGlobalDeErros extends ResponseEntityExceptionHandler {

    // Logger proprio: o "logger" herdado de ResponseEntityExceptionHandler e o
    // Commons Logging, com assinaturas diferentes das do SLF4J.
    private static final Logger log = LoggerFactory.getLogger(ManipuladorGlobalDeErros.class);

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

    /**
     * Falha ao falar com o gateway de pagamento: 502, nao 500.
     *
     * <p>A distincao importa para quem consome a API. 500 diz "o defeito e
     * nosso"; 502 diz "quem falhou foi um sistema de que dependemos". Com a
     * chave de idempotencia em maos, repetir a chamada e seguro — e e isso que
     * o cliente precisa saber.</p>
     */
    @ExceptionHandler(GatewayIndisponivelException.class)
    public ProblemDetail trataGatewayIndisponivel(GatewayIndisponivelException e) {
        return problema(HttpStatus.BAD_GATEWAY, "gateway-indisponivel",
                "Gateway indisponivel", e.getMessage());
    }

    /**
     * Webhook com assinatura ausente, invalida ou vencida: 401.
     *
     * <p>A mensagem nao diz <i>qual</i> das tres coisas falhou. Para quem tem o
     * segredo, tanto faz — nunca vai cair aqui; para quem nao tem, cada detalhe
     * e uma dica de como chegar mais perto.</p>
     */
    @ExceptionHandler(AssinaturaInvalidaException.class)
    public ProblemDetail trataAssinaturaInvalida(AssinaturaInvalidaException e) {
        // O motivo real vai para o log, onde e util para depurar configuracao.
        log.warn("Webhook recusado: {}", e.getMessage());
        return problema(HttpStatus.UNAUTHORIZED, "assinatura-invalida",
                "Assinatura invalida", "A assinatura da requisicao nao pode ser verificada");
    }

    /** Corpo de webhook fora do formato esperado: 400, reenviar igual nao adianta. */
    @ExceptionHandler(EventoMalFormadoException.class)
    public ProblemDetail trataEventoMalFormado(EventoMalFormadoException e) {
        return problema(HttpStatus.BAD_REQUEST, "evento-mal-formado",
                "Evento mal formado", e.getMessage());
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
