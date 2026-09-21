package com.joaoalcantara.pedidos.produto.dominio;

import com.joaoalcantara.pedidos.comum.erro.ConflitoException;

/**
 * Nao ha unidades suficientes disponiveis para atender a reserva.
 *
 * <p>E {@code ConflitoException} — 409, nao 422 — porque nao ha nada de errado
 * com a requisicao: ela seria valida um minuto antes. O que mudou foi o estado
 * do sistema, e o cliente que perdeu a disputa pelo ultimo item precisa
 * distinguir "voce pediu algo impossivel" de "alguem chegou na frente".</p>
 */
public class EstoqueInsuficienteException extends ConflitoException {

    public EstoqueInsuficienteException(String nomeProduto, int solicitado, int disponivel) {
        super("estoque-insuficiente",
                "Estoque insuficiente para '%s': %d solicitada(s), %d disponivel(is)"
                        .formatted(nomeProduto, solicitado, disponivel));
    }
}
