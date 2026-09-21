package com.joaoalcantara.pedidos.seguranca;

import java.io.IOException;
import java.util.List;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.joaoalcantara.pedidos.usuario.dominio.Papel;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Le o header Authorization, valida o token e popula o contexto de seguranca.
 *
 * <p>Nao rejeita a requisicao quando o token e invalido ou ausente: apenas deixa
 * o contexto vazio e segue a cadeia. Quem decide se aquela rota exigia
 * autenticacao e a configuracao de autorizacao, que ja sabe responder 401/403 no
 * formato certo. Misturar as duas responsabilidades aqui produziria erros fora
 * do padrao Problem Details — e faria o catalogo publico deixar de responder a
 * quem mandasse um token vencido.</p>
 */
@Component
public class FiltroDeAutenticacaoJwt extends OncePerRequestFilter {

    private static final String HEADER = "Authorization";
    private static final String PREFIXO = "Bearer ";

    private final ServicoDeToken servicoDeToken;

    public FiltroDeAutenticacaoJwt(ServicoDeToken servicoDeToken) {
        this.servicoDeToken = servicoDeToken;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest requisicao, HttpServletResponse resposta, FilterChain cadeia)
            throws ServletException, IOException {

        String token = extrairToken(requisicao);
        if (token != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            try {
                Claims claims = servicoDeToken.validar(token);
                Papel papel = Papel.valueOf(ServicoDeToken.papel(claims));
                UsuarioAutenticado usuario = new UsuarioAutenticado(
                        ServicoDeToken.idDoUsuario(claims),
                        ServicoDeToken.nome(claims),
                        papel);

                var autenticacao = new UsernamePasswordAuthenticationToken(
                        usuario, null, List.of(new SimpleGrantedAuthority(papel.authority())));
                autenticacao.setDetails(new WebAuthenticationDetailsSource().buildDetails(requisicao));
                SecurityContextHolder.getContext().setAuthentication(autenticacao);
            } catch (JwtException | IllegalArgumentException e) {
                SecurityContextHolder.clearContext();
            }
        }

        cadeia.doFilter(requisicao, resposta);
    }

    private String extrairToken(HttpServletRequest requisicao) {
        String header = requisicao.getHeader(HEADER);
        if (header == null || !header.startsWith(PREFIXO)) {
            return null;
        }
        String token = header.substring(PREFIXO.length()).trim();
        return token.isEmpty() ? null : token;
    }
}
