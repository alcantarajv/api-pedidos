package com.joaoalcantara.pedidos.usuario.dominio;

import java.util.Optional;

/** Porta de persistencia de usuarios. */
public interface UsuarioRepositorio {

    Usuario salvar(Usuario usuario);

    Optional<Usuario> porEmail(String email);

    Optional<Usuario> porId(Long id);

    boolean existeComEmail(String email);
}
