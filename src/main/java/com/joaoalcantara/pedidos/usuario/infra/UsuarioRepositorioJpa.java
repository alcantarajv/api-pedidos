package com.joaoalcantara.pedidos.usuario.infra;

import java.util.Optional;

import org.springframework.stereotype.Repository;

import com.joaoalcantara.pedidos.usuario.dominio.Usuario;
import com.joaoalcantara.pedidos.usuario.dominio.UsuarioRepositorio;

@Repository
class UsuarioRepositorioJpa implements UsuarioRepositorio {

    private final UsuarioSpringDataRepository jpa;

    UsuarioRepositorioJpa(UsuarioSpringDataRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Usuario salvar(Usuario usuario) {
        return jpa.save(usuario);
    }

    @Override
    public Optional<Usuario> porEmail(String email) {
        return jpa.findByEmailIgnoreCase(email);
    }

    @Override
    public Optional<Usuario> porId(Long id) {
        return jpa.findById(id);
    }

    @Override
    public boolean existeComEmail(String email) {
        return jpa.existsByEmailIgnoreCase(email);
    }
}
