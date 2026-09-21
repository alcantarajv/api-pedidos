package com.joaoalcantara.pedidos.produto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import com.joaoalcantara.pedidos.comum.PostgresDeTeste;
import com.joaoalcantara.pedidos.comum.RabbitDeTeste;
import com.joaoalcantara.pedidos.produto.dominio.Produto;
import com.joaoalcantara.pedidos.produto.dominio.ProdutoRepositorio;

/**
 * Verifica o que so o banco de verdade pode responder: que a migration e o
 * mapeamento concordam, e que as check constraints realmente barram.
 *
 * <p>Que o Hibernate aceite subir ja e parte do teste: com
 * {@code ddl-auto=validate}, uma coluna com nome ou tipo diferente do mapeado
 * impede o contexto de carregar.</p>
 */
@SpringBootTest
@Import({PostgresDeTeste.class, RabbitDeTeste.class})
class ProdutoPersistenciaIT {

    @Autowired
    private ProdutoRepositorio repositorio;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    @DisplayName("grava e le o produto com o estoque nas duas parcelas")
    void gravaELe() {
        Produto novo = new Produto("Monitor 27", "IPS, 144Hz", new BigDecimal("1899.90"), 5);
        novo.reservar(2);

        Long id = repositorio.salvar(novo).getId();

        Produto lido = repositorio.porId(id).orElseThrow();
        assertThat(lido.getPreco()).isEqualByComparingTo("1899.90");
        assertThat(lido.getEstoqueDisponivel()).isEqualTo(3);
        assertThat(lido.getEstoqueReservado()).isEqualTo(2);
        assertThat(lido.getEstoqueTotal()).isEqualTo(5);
    }

    @Test
    @DisplayName("o banco recusa estoque disponivel negativo, mesmo por SQL direto")
    void checkConstraintBarraEstoqueNegativo() {
        Long id = repositorio.salvar(new Produto("Mouse", null, new BigDecimal("199.90"), 1)).getId();

        // Passa por fora da entidade de proposito: a garantia que interessa aqui e
        // a do banco, nao a do Java. Na Etapa 4 e ela que impede duas transacoes
        // concorrentes de venderem a mesma ultima unidade.
        assertThatThrownBy(() -> jdbc.update("UPDATE produtos SET estoque_disponivel = -1 WHERE id = ?", id))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("produtos_estoque_disponivel_nao_negativo");
    }

    @Test
    @DisplayName("o banco recusa dois produtos com o mesmo nome, ignorando maiusculas")
    void indiceUnicoIgnoraCaixa() {
        repositorio.salvar(new Produto("Headset Pro", null, new BigDecimal("499.00"), 2));

        assertThatThrownBy(() -> repositorio.salvar(new Produto("headset pro", null, new BigDecimal("499.00"), 2)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
