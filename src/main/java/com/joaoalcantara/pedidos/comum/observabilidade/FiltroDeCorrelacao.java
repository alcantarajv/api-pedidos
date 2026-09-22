package com.joaoalcantara.pedidos.comum.observabilidade;

import java.io.IOException;

import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Coloca um id de correlacao no contexto de log de cada requisicao.
 *
 * <p>Se o cliente mandou {@code X-Request-Id}, ele e reaproveitado — assim o id
 * atravessa tambem a fronteira entre os sistemas de quem chama e este. Se nao
 * mandou, um e gerado.</p>
 *
 * <p>O id volta no cabecalho da resposta de proposito: quem recebeu um erro tem
 * em maos exatamente o termo de busca que encontra aquela requisicao no log.
 * Numa conversa de suporte, isso troca "aconteceu um erro ontem a tarde" por uma
 * linha exata.</p>
 *
 * <p>{@code HIGHEST_PRECEDENCE}: precisa rodar antes da cadeia de seguranca,
 * senao um 401 — justamente o que se quer investigar — sairia sem id.</p>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class FiltroDeCorrelacao extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest requisicao, HttpServletResponse resposta, FilterChain cadeia)
            throws ServletException, IOException {

        String recebido = requisicao.getHeader(Correlacao.CABECALHO);
        String correlacaoId = (recebido == null || recebido.isBlank())
                ? Correlacao.gerar()
                // Limite de tamanho: o valor vem de fora e vai para toda linha de
                // log. Sem corte, um cabecalho de 10 KB entope o log inteiro.
                : recebido.substring(0, Math.min(recebido.length(), 64));

        resposta.setHeader(Correlacao.CABECALHO, correlacaoId);

        // try/finally direto, e nao Correlacao.executarCom: embrulhar a cadeia
        // num Runnable obrigaria a converter IOException e ServletException em
        // excecao nao verificada, e o tratamento de erros passaria a ver o
        // embrulho no lugar da causa real.
        MDC.put(Correlacao.CHAVE, correlacaoId);
        try {
            cadeia.doFilter(requisicao, resposta);
        } finally {
            MDC.remove(Correlacao.CHAVE);
        }
    }
}
