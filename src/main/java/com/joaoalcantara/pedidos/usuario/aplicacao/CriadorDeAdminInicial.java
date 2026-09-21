package com.joaoalcantara.pedidos.usuario.aplicacao;

import java.time.Clock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.joaoalcantara.pedidos.comum.config.PropriedadesDeAdminInicial;
import com.joaoalcantara.pedidos.usuario.dominio.Papel;
import com.joaoalcantara.pedidos.usuario.dominio.Usuario;
import com.joaoalcantara.pedidos.usuario.dominio.UsuarioRepositorio;

/**
 * Cria o primeiro ADMIN no boot, se ele foi configurado e ainda nao existe.
 *
 * <p>Existe um problema de partida em todo sistema com papeis: o registro
 * publico cria apenas CLIENTE — se o papel viesse na requisicao, qualquer um se
 * autopromoveria — e um ADMIN so poderia ser criado por outro ADMIN. Sem um
 * ponto de partida, o sistema sobe sem nenhum administrador e as rotas de
 * gestao do catalogo ficam inalcancaveis.</p>
 *
 * <p>A saida e um bootstrap por configuracao, o mesmo padrao de Grafana,
 * Keycloak e Jenkins. Duas salvaguardas o tornam seguro:</p>
 *
 * <ul>
 *   <li>Nao ha valor padrao. Sem {@code ADMIN_INICIAL_EMAIL} e
 *       {@code ADMIN_INICIAL_SENHA} definidos, nada acontece — o sistema nunca
 *       sobe com credencial de administrador conhecida, que e exatamente como
 *       instalacoes de exemplo acabam invadidas.</li>
 *   <li>E idempotente. Se ja existe conta com aquele e-mail, nada e feito: a
 *       senha em producao nao e sobrescrita a cada deploy, e a variavel pode
 *       continuar definida sem efeito colateral.</li>
 * </ul>
 */
@Component
public class CriadorDeAdminInicial implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(CriadorDeAdminInicial.class);

    private final PropriedadesDeAdminInicial propriedades;
    private final UsuarioRepositorio usuarios;
    private final PasswordEncoder codificador;
    private final Clock relogio;

    public CriadorDeAdminInicial(PropriedadesDeAdminInicial propriedades, UsuarioRepositorio usuarios,
                                 PasswordEncoder codificador, Clock relogio) {
        this.propriedades = propriedades;
        this.usuarios = usuarios;
        this.codificador = codificador;
        this.relogio = relogio;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!propriedades.configurado()) {
            return;
        }

        String email = propriedades.email().trim().toLowerCase();

        if (usuarios.existeComEmail(email)) {
            log.info("Admin inicial '{}' ja existe; nenhuma alteracao feita.", email);
            return;
        }

        Usuario admin = new Usuario(
                propriedades.nome(),
                email,
                codificador.encode(propriedades.senha()),
                Papel.ADMIN,
                relogio.instant());

        usuarios.salvar(admin);
        log.warn("Admin inicial '{}' criado a partir da configuracao. "
                + "Troque a senha e remova a variavel de ambiente apos o primeiro acesso.", email);
    }
}
