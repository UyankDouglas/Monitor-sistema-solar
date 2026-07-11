package com.solarmonitor.energy.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.time.Instant;

/**
 * Chave composta de {@link EnergySample}: (inversor, instante da leitura).
 * O TimescaleDB exige que a coluna de particionamento ({@code sampled_at})
 * faça parte da chave primária da hypertable.
 */
@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class EnergySampleId implements Serializable {

    @Column(name = "inverter_id", nullable = false)
    private Long inverterId;

    @Column(name = "sampled_at", nullable = false)
    private Instant sampledAt;
}
