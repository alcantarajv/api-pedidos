package com.joaoalcantara.pedidos.consumo.infra;

import org.springframework.stereotype.Repository;

import com.joaoalcantara.pedidos.consumo.dominio.MensagemConsumida;
import com.joaoalcantara.pedidos.consumo.dominio.MensagemConsumidaRepositorio;

@Repository
class MensagemConsumidaRepositorioJpa implements MensagemConsumidaRepositorio {

    private final MensagemConsumidaSpringDataRepository jpa;

    MensagemConsumidaRepositorioJpa(MensagemConsumidaSpringDataRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public MensagemConsumida salvar(MensagemConsumida registro) {
        return jpa.save(registro);
    }

    @Override
    public boolean jaConsumida(String idMensagem, String consumidor) {
        return jpa.existsByIdMensagemAndConsumidor(idMensagem, consumidor);
    }

    @Override
    public int apagarAnterioresA(java.time.Instant limite) {
        return jpa.deleteByConsumidaEmBefore(limite);
    }
}
