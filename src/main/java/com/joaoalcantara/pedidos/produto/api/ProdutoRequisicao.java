package com.joaoalcantara.pedidos.produto.api;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Entrada de criacao/atualizacao de produto.
 *
 * <p>Primeira camada de validacao, a de formato. Repare que {@code preco} e
 * {@code BigDecimal} ja no DTO: se entrasse como {@code double}, o valor
 * chegaria arredondado antes de qualquer regra ter a chance de opinar.</p>
 *
 * <p>O estoque inicial so e considerado na criacao. Na atualizacao ele e
 * ignorado, porque mexer em estoque tem endpoint proprio — misturar as duas
 * coisas faria uma correcao de preco poder desfazer uma entrada de mercadoria
 * por descuido de quem montou o JSON.</p>
 */
public record ProdutoRequisicao(
        @NotBlank(message = "e obrigatorio")
        @Size(max = 140, message = "deve ter no maximo 140 caracteres") String nome,

        @Size(max = 2000, message = "deve ter no maximo 2000 caracteres") String descricao,

        @NotNull(message = "e obrigatorio")
        @DecimalMin(value = "0.00", message = "nao pode ser negativo")
        @Digits(integer = 10, fraction = 2, message = "formato monetario invalido") BigDecimal preco,

        @NotNull(message = "e obrigatorio")
        @Min(value = 0, message = "nao pode ser negativo") Integer estoqueInicial) {
}
