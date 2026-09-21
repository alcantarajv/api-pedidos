package com.joaoalcantara.pedidos.pedido.dominio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

import com.joaoalcantara.pedidos.produto.dominio.Produto;
import com.joaoalcantara.pedidos.usuario.dominio.Papel;
import com.joaoalcantara.pedidos.usuario.dominio.Usuario;

/**
 * A maquina de estados do pedido, verificada por inteiro.
 *
 * <p>Nao basta testar os caminhos felizes: o valor de uma maquina de estados
 * esta no que ela <b>recusa</b>. Por isso o teste principal percorre a matriz
 * completa — todos os estados contra todos os destinos — e exige que cada
 * combinacao fora do grafo seja rejeitada.</p>
 */
class CicloDeVidaDoPedidoTest {

    private static final Instant AGORA = Instant.parse("2026-09-21T12:00:00Z");

    private Pedido pedidoEm(StatusPedido status) {
        Usuario cliente = new Usuario("Joao", "joao@exemplo.com", "hash", Papel.CLIENTE, AGORA);
        Pedido pedido = new Pedido(cliente, "chave-" + status, AGORA);
        pedido.adicionarItem(new Produto("Teclado", "d", new BigDecimal("100.00"), 10), 1);

        // Caminha pelo grafo ate o estado desejado, usando so transicoes validas.
        for (StatusPedido passo : caminhoAte(status)) {
            aplicar(pedido, passo);
        }
        return pedido;
    }

    /** Sequencia de transicoes validas que leva de AGUARDANDO_PAGAMENTO ate o alvo. */
    private static List<StatusPedido> caminhoAte(StatusPedido alvo) {
        return switch (alvo) {
            case AGUARDANDO_PAGAMENTO -> List.of();
            case PAGO -> List.of(StatusPedido.PAGO);
            case SEPARANDO -> List.of(StatusPedido.PAGO, StatusPedido.SEPARANDO);
            case ENVIADO -> List.of(StatusPedido.PAGO, StatusPedido.SEPARANDO, StatusPedido.ENVIADO);
            case ENTREGUE -> List.of(StatusPedido.PAGO, StatusPedido.SEPARANDO,
                    StatusPedido.ENVIADO, StatusPedido.ENTREGUE);
            case CANCELADO -> List.of(StatusPedido.CANCELADO);
            case REEMBOLSADO -> List.of(StatusPedido.PAGO, StatusPedido.REEMBOLSADO);
        };
    }

    private static void aplicar(Pedido pedido, StatusPedido destino) {
        switch (destino) {
            case PAGO -> pedido.marcarComoPago();
            case SEPARANDO -> pedido.iniciarSeparacao();
            case ENVIADO -> pedido.marcarComoEnviado();
            case ENTREGUE -> pedido.marcarComoEntregue();
            case CANCELADO -> pedido.cancelar();
            case REEMBOLSADO -> pedido.reembolsar();
            case AGUARDANDO_PAGAMENTO -> throw new IllegalArgumentException("estado inicial, nao e destino");
        }
    }

    static List<org.junit.jupiter.params.provider.Arguments> todasAsCombinacoes() {
        List<org.junit.jupiter.params.provider.Arguments> combinacoes = new ArrayList<>();
        for (StatusPedido origem : StatusPedido.values()) {
            for (StatusPedido destino : StatusPedido.values()) {
                if (destino != StatusPedido.AGUARDANDO_PAGAMENTO) {
                    combinacoes.add(org.junit.jupiter.params.provider.Arguments.of(origem, destino));
                }
            }
        }
        return combinacoes;
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @MethodSource("todasAsCombinacoes")
    @DisplayName("a matriz inteira de transicoes obedece ao grafo declarado")
    void matrizCompleta(StatusPedido origem, StatusPedido destino) {
        Pedido pedido = pedidoEm(origem);

        if (origem.permiteIrPara(destino)) {
            assertThatCode(() -> aplicar(pedido, destino)).doesNotThrowAnyException();
            assertThat(pedido.getStatus()).isEqualTo(destino);
        } else {
            assertThatThrownBy(() -> aplicar(pedido, destino))
                    .isInstanceOf(TransicaoInvalidaException.class);
            assertThat(pedido.getStatus())
                    .as("uma transicao recusada nao pode deixar o pedido em estado intermediario")
                    .isEqualTo(origem);
        }
    }

    @ParameterizedTest
    @EnumSource(value = StatusPedido.class, names = {"ENTREGUE", "CANCELADO", "REEMBOLSADO"})
    @DisplayName("estado final nao aceita nenhuma transicao")
    void estadosFinais(StatusPedido finalizado) {
        assertThat(finalizado.ehFinal()).isTrue();
        assertThat(finalizado.proximos()).isEmpty();
    }

    @Test
    @DisplayName("o caminho feliz completo funciona de ponta a ponta")
    void caminhoFeliz() {
        Pedido pedido = pedidoEm(StatusPedido.AGUARDANDO_PAGAMENTO);

        pedido.marcarComoPago();
        pedido.iniciarSeparacao();
        pedido.marcarComoEnviado();
        pedido.marcarComoEntregue();

        assertThat(pedido.getStatus()).isEqualTo(StatusPedido.ENTREGUE);
        assertThat(pedido.getStatus().ehFinal()).isTrue();
    }

    @Test
    @DisplayName("pedido entregue nao volta para pago")
    void entregueNaoRetrocede() {
        Pedido pedido = pedidoEm(StatusPedido.ENTREGUE);

        assertThatThrownBy(pedido::marcarComoPago)
                .isInstanceOf(TransicaoInvalidaException.class)
                .hasMessageContaining("ENTREGUE")
                .hasMessageContaining("PAGO");
    }

    @Test
    @DisplayName("pedido cancelado nao avanca")
    void canceladoNaoAvanca() {
        Pedido pedido = pedidoEm(StatusPedido.CANCELADO);

        assertThatThrownBy(pedido::marcarComoPago).isInstanceOf(TransicaoInvalidaException.class);
        assertThatThrownBy(pedido::cancelar).isInstanceOf(TransicaoInvalidaException.class);
    }

    @Test
    @DisplayName("so o pedido aguardando pagamento segura reserva de estoque")
    void reservaDeEstoqueSoEnquantoAguarda() {
        assertThat(pedidoEm(StatusPedido.AGUARDANDO_PAGAMENTO).mantemReservaDeEstoque()).isTrue();
        assertThat(pedidoEm(StatusPedido.PAGO).mantemReservaDeEstoque()).isFalse();
        assertThat(pedidoEm(StatusPedido.CANCELADO).mantemReservaDeEstoque()).isFalse();
    }

    @Test
    @DisplayName("pedido que saiu do estado inicial nao aceita novos itens")
    void pedidoPagoNaoAceitaItem() {
        Pedido pedido = pedidoEm(StatusPedido.PAGO);

        assertThatThrownBy(() -> pedido.adicionarItem(new Produto("Mouse", "d", new BigDecimal("50.00"), 5), 1))
                .isInstanceOf(IllegalStateException.class);
    }
}
