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

/** Chave composta de {@link MpptReading}: (inversor, instante, índice da string). */
@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class MpptReadingId implements Serializable {

    @Column(name = "inverter_id", nullable = false)
    private Long inverterId;

    @Column(name = "sampled_at", nullable = false)
    private Instant sampledAt;

    /** Índice da string MPPT, iniciando em 1 (SUN-10K tem 1 e 2). */
    @Column(name = "string_index", nullable = false)
    private Short stringIndex;
}
