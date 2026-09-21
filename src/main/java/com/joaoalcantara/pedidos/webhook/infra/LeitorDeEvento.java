package com.joaoalcantara.pedidos.webhook.infra;

import org.springframework.stereotype.Component;

import com.joaoalcantara.pedidos.webhook.aplicacao.EventoDoGateway;
import com.joaoalcantara.pedidos.webhook.dominio.EventoMalFormadoException;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Extrai do JSON do gateway os poucos campos de que o sistema depende.
 *
 * <p>Le como arvore ({@code JsonNode}) em vez de mapear para uma classe. O
 * payload do Stripe tem dezenas de campos que mudam entre versoes da API, e um
 * mapeamento estrito quebraria a cada campo novo — justamente no componente que
 * precisa continuar funcionando quando o fornecedor muda algo.</p>
 */
@Component
public class LeitorDeEvento {

    private final ObjectMapper objectMapper;

    public LeitorDeEvento(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public EventoDoGateway ler(String corpoCru) {
        JsonNode raiz;
        try {
            raiz = objectMapper.readTree(corpoCru);
        } catch (JacksonException e) {
            throw new EventoMalFormadoException("Corpo do webhook nao e um JSON valido");
        }

        String id = texto(raiz, "id");
        String tipo = texto(raiz, "type");
        // data.object e o recurso a que o evento se refere: para os eventos que
        // tratamos, o PaymentIntent.
        String idDaCobranca = texto(raiz.path("data").path("object"), "id");

        if (id == null || tipo == null || idDaCobranca == null) {
            throw new EventoMalFormadoException("Webhook sem id, type ou data.object.id");
        }
        return new EventoDoGateway(id, tipo, idDaCobranca);
    }

    private static String texto(JsonNode no, String campo) {
        JsonNode valor = no.path(campo);
        return valor.isTextual() ? valor.asString() : null;
    }
}
