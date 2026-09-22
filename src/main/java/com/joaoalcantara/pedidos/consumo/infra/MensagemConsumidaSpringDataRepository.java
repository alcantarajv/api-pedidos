package com.joaoalcantara.pedidos.consumo.infra;

import org.springframework.data.jpa.repository.JpaRepository;

import com.joaoalcantara.pedidos.consumo.dominio.MensagemConsumida;

interface MensagemConsumidaSpringDataRepository extends JpaRepository<MensagemConsumida, Long> {

    boolean existsByIdMensagemAndConsumidor(String idMensagem, String consumidor);

    int deleteByConsumidaEmBefore(java.time.Instant limite);
}
