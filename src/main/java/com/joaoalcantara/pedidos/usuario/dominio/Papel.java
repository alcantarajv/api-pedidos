package com.joaoalcantara.pedidos.usuario.dominio;

/**
 * Papel do usuario no sistema.
 *
 * <p>Um papel unico por usuario cobre o dominio (quem compra x quem administra o
 * catalogo) sem introduzir a complexidade de um modelo de permissoes granular
 * que o projeto nao usaria.</p>
 */
public enum Papel {
    CLIENTE,
    ADMIN;

    /** Spring Security espera o prefixo ROLE_ para o conceito de "role". */
    public String authority() {
        return "ROLE_" + name();
    }
}
