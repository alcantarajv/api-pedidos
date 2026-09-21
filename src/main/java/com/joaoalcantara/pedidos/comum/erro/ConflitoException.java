package com.joaoalcantara.pedidos.comum.erro;

/**
 * O estado atual do sistema impede a operacao — tipicamente uma disputa por
 * recurso escasso, como o ultimo item do estoque. Traduzida para 409 Conflict.
 */
public class ConflitoException extends RuntimeException {

    private final String codigo;

    public ConflitoException(String codigo, String mensagem) {
        super(mensagem);
        this.codigo = codigo;
    }

    public String codigo() {
        return codigo;
    }
}
