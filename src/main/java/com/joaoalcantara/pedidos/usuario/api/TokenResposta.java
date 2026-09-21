package com.joaoalcantara.pedidos.usuario.api;

import java.time.Instant;

public record TokenResposta(String token, String tipo, Instant expiraEm, long expiraEmSegundos) {

    public static TokenResposta bearer(String token, Instant expiraEm, long expiraEmSegundos) {
        return new TokenResposta(token, "Bearer", expiraEm, expiraEmSegundos);
    }
}
