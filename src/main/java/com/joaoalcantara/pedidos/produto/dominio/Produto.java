package com.joaoalcantara.pedidos.produto.dominio;

import java.math.BigDecimal;
import java.util.Objects;

import com.joaoalcantara.pedidos.comum.erro.RegraDeNegocioException;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Item do catalogo, com o controle de estoque em duas parcelas.
 *
 * <p>O estoque e separado em <b>disponivel</b> (pode ser prometido a um pedido
 * novo) e <b>reservado</b> (ja prometido a um pedido aguardando pagamento). A
 * soma das duas e o que existe fisicamente no deposito.</p>
 *
 * <p>Um contador unico tambem funcionaria, mas perderia informacao: com ele nao
 * da para responder "quantas unidades estao presas em pedidos que ainda podem
 * expirar?", que e exatamente o que o administrador precisa saber para decidir
 * se repoe estoque. A separacao tambem da significado a cada passo do ciclo:</p>
 *
 * <pre>
 *   criacao do pedido   disponivel -> reservado   (reservar)
 *   pagamento aprovado  reservado  -> saiu        (confirmarVenda)
 *   cancelou / expirou  reservado  -> disponivel  (devolverReserva)
 * </pre>
 *
 * <p>Nenhum desses metodos sozinho resolve duas transacoes disputando a ultima
 * unidade — isso e problema da Etapa 4, e a rede de seguranca esta no banco, na
 * check constraint que proibe estoque negativo.</p>
 */
@Entity
@Table(name = "produtos")
public class Produto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "nome", nullable = false, length = 140)
    private String nome;

    @Column(name = "descricao", length = 2000)
    private String descricao;

    @Column(name = "preco", nullable = false, precision = 12, scale = 2)
    private BigDecimal preco;

    @Column(name = "estoque_disponivel", nullable = false)
    private int estoqueDisponivel;

    @Column(name = "estoque_reservado", nullable = false)
    private int estoqueReservado;

    @Column(name = "ativo", nullable = false)
    private boolean ativo;

    protected Produto() {
        // exigido pelo JPA
    }

    public Produto(String nome, String descricao, BigDecimal preco, int estoqueDisponivel) {
        this.nome = Objects.requireNonNull(nome, "nome");
        this.descricao = descricao;
        this.preco = Objects.requireNonNull(preco, "preco");
        this.estoqueDisponivel = exigirNaoNegativo(estoqueDisponivel);
        this.estoqueReservado = 0;
        this.ativo = true;
    }

    public void atualizarDados(String nome, String descricao, BigDecimal preco) {
        this.nome = Objects.requireNonNull(nome, "nome");
        this.descricao = descricao;
        this.preco = Objects.requireNonNull(preco, "preco");
    }

    /**
     * Reserva unidades para um pedido que acaba de nascer.
     *
     * @throws EstoqueInsuficienteException quando nao ha disponivel suficiente —
     *         resultado legitimo de negocio, que vira 409 para o cliente
     */
    public void reservar(int quantidade) {
        exigirPositivo(quantidade);
        if (quantidade > estoqueDisponivel) {
            throw new EstoqueInsuficienteException(nome, quantidade, estoqueDisponivel);
        }
        this.estoqueDisponivel -= quantidade;
        this.estoqueReservado += quantidade;
    }

    /**
     * Confirma a saida das unidades ja reservadas, apos o pagamento aprovado.
     *
     * <p>O disponivel nao se mexe aqui: essas unidades sairam dele na reserva.
     * Confirmar o que nao foi reservado e defeito de programacao, nao situacao
     * de negocio — por isso {@link IllegalStateException} e nao uma excecao
     * traduzida para resposta HTTP.</p>
     */
    public void confirmarVenda(int quantidade) {
        exigirPositivo(quantidade);
        exigirReservaSuficiente(quantidade);
        this.estoqueReservado -= quantidade;
    }

    /** Devolve ao disponivel unidades reservadas por um pedido cancelado ou expirado. */
    public void devolverReserva(int quantidade) {
        exigirPositivo(quantidade);
        exigirReservaSuficiente(quantidade);
        this.estoqueReservado -= quantidade;
        this.estoqueDisponivel += quantidade;
    }

    /**
     * Ajuste administrativo do estoque disponivel — entrada de mercadoria,
     * inventario, perda.
     *
     * <p>Mexe so no disponivel, de proposito. O reservado pertence a pedidos de
     * clientes reais; deixar o administrador reduzi-lo por aqui significaria
     * desfazer promessas ja feitas sem passar pelo cancelamento do pedido.</p>
     */
    public void ajustarEstoqueDisponivel(int novoDisponivel) {
        this.estoqueDisponivel = exigirNaoNegativo(novoDisponivel);
    }

    public void ativar() {
        this.ativo = true;
    }

    /**
     * Desativa o produto. Nao devolve nem cancela o que ja esta reservado:
     * tirar do catalogo impede pedidos NOVOS, e nao desfaz os que existem.
     */
    public void desativar() {
        this.ativo = false;
    }

    /** Produto inativo nao entra em pedido novo, mesmo com estoque sobrando. */
    public void exigirDisponivelParaVenda() {
        if (!ativo) {
            throw new RegraDeNegocioException("produto-inativo",
                    "O produto '%s' nao esta disponivel para venda".formatted(nome));
        }
    }

    private void exigirReservaSuficiente(int quantidade) {
        if (quantidade > estoqueReservado) {
            throw new IllegalStateException(
                    "Tentativa de baixar %d unidade(s) com apenas %d reservada(s) em '%s'"
                            .formatted(quantidade, estoqueReservado, nome));
        }
    }

    private static void exigirPositivo(int quantidade) {
        if (quantidade <= 0) {
            throw new IllegalArgumentException("A quantidade deve ser positiva, recebida: " + quantidade);
        }
    }

    private static int exigirNaoNegativo(int quantidade) {
        if (quantidade < 0) {
            throw new IllegalArgumentException("O estoque nao pode ser negativo, recebido: " + quantidade);
        }
        return quantidade;
    }

    public Long getId() {
        return id;
    }

    public String getNome() {
        return nome;
    }

    public String getDescricao() {
        return descricao;
    }

    public BigDecimal getPreco() {
        return preco;
    }

    public int getEstoqueDisponivel() {
        return estoqueDisponivel;
    }

    public int getEstoqueReservado() {
        return estoqueReservado;
    }

    /** Quanto existe fisicamente: o que pode ser vendido mais o que esta prometido. */
    public int getEstoqueTotal() {
        return estoqueDisponivel + estoqueReservado;
    }

    public boolean isAtivo() {
        return ativo;
    }
}
