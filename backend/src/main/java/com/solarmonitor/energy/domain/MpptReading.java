package com.solarmonitor.energy.domain;

import com.solarmonitor.plant.domain.Inverter;
import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.domain.Persistable;

import java.math.BigDecimal;

/**
 * Leitura de uma string MPPT em um instante (hypertable TimescaleDB).
 * Associa-se à {@link EnergySample} do mesmo (inversor, instante) apenas
 * logicamente — sem FK física (limitação de hypertables).
 *
 * <p>{@link Persistable} com {@code isNew()} sempre {@code true}: append-only,
 * mesmo racional documentado em {@link EnergySample}.</p>
 */
@Entity
@Table(name = "mppt_reading")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MpptReading implements Persistable<MpptReadingId> {

    @EmbeddedId
    private MpptReadingId id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("inverterId")
    @JoinColumn(name = "inverter_id", nullable = false)
    private Inverter inverter;

    @Column(name = "voltage", precision = 6, scale = 1)
    private BigDecimal voltage;

    @Column(name = "current_a", precision = 7, scale = 2)
    private BigDecimal currentA;

    @Column(name = "power_w")
    private Integer powerW;

    @Override
    @Transient
    public boolean isNew() {
        return true;
    }
}
