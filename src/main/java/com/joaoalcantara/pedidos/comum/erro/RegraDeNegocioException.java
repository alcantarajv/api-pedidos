package com.joaoalcantara.pedidos.comum.erro;

/**
 * Uma regra de negocio foi violada — a requisicao e sintaticamente valida, mas o
 * dominio nao a aceita (produto inativo, transicao de estado proibida, etc.).
 * Traduzida para 422 Unprocessable Entity.
 */
public class RegraDeNegocioException extends RuntimeException {

    private final String codigo;

    public RegraDeNegocioException(String codigo, String mensagem) {
        super(mensagem);
        this.codigo = codigo;
    }

    public String codigo() {
        return codigo;
    }
}
