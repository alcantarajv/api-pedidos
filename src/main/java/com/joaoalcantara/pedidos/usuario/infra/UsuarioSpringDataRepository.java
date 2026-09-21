package com.joaoalcantara.pedidos.usuario.infra;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.joaoalcantara.pedidos.usuario.dominio.Usuario;

interface UsuarioSpringDataRepository extends JpaRepository<Usuario, Long> {

    Optional<Usuario> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);
}
