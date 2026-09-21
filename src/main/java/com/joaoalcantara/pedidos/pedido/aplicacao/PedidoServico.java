package com.joaoalcantara.pedidos.pedido.aplicacao;

import java.util.List;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.joaoalcantara.pedidos.comum.erro.RecursoNaoEncontradoException;
import com.joaoalcantara.pedidos.pedido.api.PedidoRequisicao;
import com.joaoalcantara.pedidos.pedido.dominio.Pedido;
import com.joaoalcantara.pedidos.pedido.dominio.PedidoRepositorio;
import com.joaoalcantara.pedidos.seguranca.UsuarioAutenticado;

/** Criacao e consulta de pedidos. */
@Service
public class PedidoServico {

    private final PedidoRepositorio pedidos;
    private final CriadorDePedido criador;
    private final CanceladorDePedido cancelador;

    public PedidoServico(PedidoRepositorio pedidos, CriadorDePedido criador, CanceladorDePedido cancelador) {
        this.pedidos = pedidos;
        this.criador = criador;
        this.cancelador = cancelador;
    }

    /**
     * Cria o pedido de forma idempotente pela chave do cabecalho
     * {@code Idempotency-Key}. Ha tres caminhos:
     *
     * <ol>
     *   <li><b>Chave nova:</b> o pedido e criado e o estoque, reservado.</li>
     *   <li><b>Chave ja usada por este usuario:</b> devolve o pedido anterior,
     *       sem reservar nada de novo. E o caso do cliente que perdeu a resposta
     *       por timeout e reenviou a requisicao.</li>
     *   <li><b>Duas requisicoes com a mesma chave ao mesmo tempo:</b> as duas
     *       passam pela consulta do caso 2 sem achar nada e as duas tentam
     *       gravar. O indice unico derruba a segunda, que entao le e devolve o
     *       pedido da primeira.</li>
     * </ol>
     *
     * <p>Os casos 2 e 3 nao sao redundantes: a consulta previa resolve o caso
     * comum sem provocar erro no banco, e o indice unico fecha a janela entre
     * consultar e gravar, que nenhuma consulta consegue fechar sozinha. E o
     * mesmo raciocinio que a Etapa 7 aplicara ao webhook do gateway.</p>
     *
     * <p>Este metodo <b>nao</b> e transacional de proposito: o caso 3 precisa
     * ler o banco depois que a transacao que falhou terminou. Ver
     * {@link CriadorDePedido}.</p>
     */
    public ResultadoDeCriacao criar(UsuarioAutenticado autenticado, String chaveIdempotencia,
                                    PedidoRequisicao requisicao) {

        var existente = pedidos.porChaveDeIdempotencia(autenticado.id(), chaveIdempotencia);
        if (existente.isPresent()) {
            return ResultadoDeCriacao.jaExistia(carregarComItens(existente.get().getId()));
        }

        try {
            Pedido criado = criador.criar(autenticado.id(), chaveIdempotencia, requisicao);
            return ResultadoDeCriacao.novo(carregarComItens(criado.getId()));
        } catch (DataIntegrityViolationException e) {
            // A transacao anterior ja terminou em rollback — inclusive as reservas
            // de estoque que ela tinha feito. Esta leitura roda em transacao nova.
            Pedido vencedor = pedidos.porChaveDeIdempotencia(autenticado.id(), chaveIdempotencia)
                    .orElseThrow(() -> e);
            return ResultadoDeCriacao.jaExistia(carregarComItens(vencedor.getId()));
        }
    }

    /**
     * Cancela o pedido, devolvendo o estoque reservado.
     *
     * <p>Quem pode cancelar: o dono do pedido ou um ADMIN. A checagem reusa
     * {@link #buscar} — e por isso um cliente que tenta cancelar o pedido de
     * outro recebe 404, e nao 403: responder 403 confirmaria que aquele pedido
     * existe.</p>
     */
    public Pedido cancelar(UsuarioAutenticado autenticado, Long id) {
        buscar(autenticado, id);
        cancelador.cancelar(id);
        return carregarComItens(id);
    }

    @Transactional(readOnly = true)
    public Pedido buscar(UsuarioAutenticado autenticado, Long id) {
        Pedido pedido = carregarComItens(id);

        // 404, e nao 403, quando o pedido e de outro cliente: responder 403
        // confirmaria que aquele pedido existe, o que ja e vazamento.
        if (!autenticado.ehAdmin() && !pedido.pertenceA(autenticado.id())) {
            throw RecursoNaoEncontradoException.de("Pedido", id);
        }
        return pedido;
    }

    @Transactional(readOnly = true)
    public List<Pedido> listar(UsuarioAutenticado autenticado) {
        return autenticado.ehAdmin() ? pedidos.listarTodos() : pedidos.listarDoUsuario(autenticado.id());
    }

    @Transactional(readOnly = true)
    public Pedido carregarComItens(Long id) {
        return pedidos.porIdComItens(id)
                .orElseThrow(() -> RecursoNaoEncontradoException.de("Pedido", id));
    }

    /**
     * Diz se o pedido acabou de nascer ou ja existia.
     *
     * <p>O controller usa isso para responder 201 na criacao e 200 no reenvio: o
     * corpo e o mesmo, mas o status conta ao cliente o que de fato aconteceu.</p>
     */
    public record ResultadoDeCriacao(Pedido pedido, boolean criadoAgora) {

        static ResultadoDeCriacao novo(Pedido pedido) {
            return new ResultadoDeCriacao(pedido, true);
        }

        static ResultadoDeCriacao jaExistia(Pedido pedido) {
            return new ResultadoDeCriacao(pedido, false);
        }
    }
}
