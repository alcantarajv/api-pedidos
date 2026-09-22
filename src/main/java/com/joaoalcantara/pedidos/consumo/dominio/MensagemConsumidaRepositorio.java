package com.joaoalcantara.pedidos.consumo.dominio;

/** Porta de persistencia do registro de consumo. */
public interface MensagemConsumidaRepositorio {

    MensagemConsumida salvar(MensagemConsumida registro);

    boolean jaConsumida(String idMensagem, String consumidor);

    /** Remove registros anteriores ao limite. Devolve quantos sairam. */
    int apagarAnterioresA(java.time.Instant limite);
}
