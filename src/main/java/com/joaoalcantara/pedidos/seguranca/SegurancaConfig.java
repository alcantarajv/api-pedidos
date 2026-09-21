package com.joaoalcantara.pedidos.seguranca;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import com.joaoalcantara.pedidos.usuario.dominio.Papel;

/**
 * Configuracao de autenticacao e autorizacao.
 *
 * <p>Todas as regras de acesso por rota ficam declaradas aqui, num unico bloco
 * legivel, em vez de espalhadas em {@code @PreAuthorize} pelos controllers.
 * Auditar quem pode o que vira a leitura de uma tela.</p>
 */
@Configuration
public class SegurancaConfig {

    private final FiltroDeAutenticacaoJwt filtroJwt;
    private final PontoDeEntradaNaoAutorizado pontoDeEntrada;
    private final ManipuladorDeAcessoNegado acessoNegado;

    public SegurancaConfig(FiltroDeAutenticacaoJwt filtroJwt,
                           PontoDeEntradaNaoAutorizado pontoDeEntrada,
                           ManipuladorDeAcessoNegado acessoNegado) {
        this.filtroJwt = filtroJwt;
        this.pontoDeEntrada = pontoDeEntrada;
        this.acessoNegado = acessoNegado;
    }

    @Bean
    public SecurityFilterChain cadeiaDeFiltros(HttpSecurity http) throws Exception {
        return http
                // API stateless com token no header: nao ha cookie de sessao para
                // um site terceiro reaproveitar, entao CSRF nao se aplica.
                .csrf(csrf -> csrf.disable())
                .sessionManagement(sessao -> sessao.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(rotas -> rotas
                        .requestMatchers(HttpMethod.POST, "/api/auth/registrar", "/api/auth/login").permitAll()
                        .requestMatchers("/actuator/health/**").permitAll()

                        // O gateway chama sem token — nao existe usuario numa
                        // chamada feita por outro servidor. Quem autentica esta
                        // rota e a assinatura HMAC do corpo, verificada antes de
                        // qualquer processamento.
                        .requestMatchers(HttpMethod.POST, "/api/webhooks/**").permitAll()

                        // Catalogo: leitura aberta, escrita so para ADMIN. A ordem
                        // importa — o matcher mais especifico (GET) vem antes do
                        // geral, senao o geral engoliria as leituras publicas.
                        .requestMatchers(HttpMethod.GET, "/api/produtos", "/api/produtos/*").permitAll()
                        .requestMatchers("/api/produtos/**").hasRole(Papel.ADMIN.name())

                        .anyRequest().authenticated())
                .exceptionHandling(erros -> erros
                        .authenticationEntryPoint(pontoDeEntrada)
                        .accessDeniedHandler(acessoNegado))
                .addFilterBefore(filtroJwt, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    /**
     * BCrypt com o custo padrao (10).
     *
     * <p>A escolha aqui nao e "qual hash e mais rapido" — e o contrario. BCrypt e
     * deliberadamente lento e tem sal embutido, o que torna inviavel testar
     * bilhoes de senhas por segundo contra um vazamento do banco. Hash generico
     * rapido (SHA-256, MD5) e o erro classico neste ponto: otimo para integridade
     * de arquivo, pessimo para senha.</p>
     */
    @Bean
    public PasswordEncoder codificadorDeSenha() {
        return new BCryptPasswordEncoder();
    }
}
