package com.joaoalcantara.pedidos.seguranca;

import com.joaoalcantara.pedidos.usuario.dominio.Papel;

/**
 * Identidade extraida do token e colocada no contexto de seguranca.
 *
 * <p>Carregar o id junto do papel e o que permitira, na Etapa 4, a regra "um
 * CLIENTE so ve os proprios pedidos" ser aplicada no servico sem uma consulta
 * extra por requisicao.</p>
 */
public record UsuarioAutenticado(Long id, String nome, Papel papel) {

    public boolean ehAdmin() {
        return papel == Papel.ADMIN;
    }
}
