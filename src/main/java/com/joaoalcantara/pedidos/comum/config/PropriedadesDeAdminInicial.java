package com.joaoalcantara.pedidos.comum.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Credenciais do administrador criado no primeiro boot, se houver.
 *
 * <p>Todos os campos sao opcionais e sem valor padrao util: faltando e-mail ou
 * senha, o bootstrap simplesmente nao roda. A senha vem de variavel de ambiente
 * e nunca do repositorio.</p>
 */
@ConfigurationProperties(prefix = "pedidos.admin-inicial")
public record PropriedadesDeAdminInicial(String nome, String email, String senha) {

    public PropriedadesDeAdminInicial {
        nome = (nome == null || nome.isBlank()) ? "Administrador" : nome;
    }

    /** So considera configurado quando ha e-mail e senha utilizaveis. */
    public boolean configurado() {
        return email != null && !email.isBlank() && senha != null && !senha.isBlank();
    }
}
