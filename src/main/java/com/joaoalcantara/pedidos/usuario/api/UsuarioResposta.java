package com.joaoalcantara.pedidos.usuario.api;

import com.joaoalcantara.pedidos.usuario.dominio.Papel;
import com.joaoalcantara.pedidos.usuario.dominio.Usuario;

/** Nunca inclui o hash da senha: o que nao sai da API nao vaza em log de cliente. */
public record UsuarioResposta(Long id, String nome, String email, Papel papel) {

    public static UsuarioResposta de(Usuario usuario) {
        return new UsuarioResposta(usuario.getId(), usuario.getNome(), usuario.getEmail(), usuario.getPapel());
    }
}
