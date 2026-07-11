package com.solarmonitor.energy.domain;

import com.solarmonitor.common.domain.AuditableEntity;
import com.solarmonitor.plant.domain.Inverter;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Consolidação diária por inversor, calculada pelo scheduler a partir de
 * {@link EnergySample}. Uma linha por (inversor, dia) — upsert idempotente.
 */
@Entity
@Table(name = "daily_generation",
        uniqueConstraints = @UniqueConstraint(name = "uq_daily_generation",
                columnNames = {"inverter_id", "generation_date"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DailyGeneration extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "inverter_id", nullable = false)
    private Inverter inverter;

    @Column(name = "generation_date", nullable = false)
    private LocalDate generationDate;

    @Column(name = "energy_kwh", nullable = false, precision = 10, scale = 3)
    @Builder.Default
    private BigDecimal energyKwh = BigDecimal.ZERO;

    @Column(name = "peak_power_w")
    private Integer peakPowerW;

    @Column(name = "peak_at")
    private Instant peakAt;

    @Column(name = "min_power_w")
    private Integer minPowerW;

    @Column(name = "consumption_kwh", precision = 10, scale = 3)
    private BigDecimal consumptionKwh;

    @Column(name = "export_kwh", precision = 10, scale = 3)
    private BigDecimal exportKwh;

    @Column(name = "import_kwh", precision = 10, scale = 3)
    private BigDecimal importKwh;

    /** Energia gerada e consumida localmente (geração − exportação). */
    @Column(name = "self_consumption_kwh", precision = 10, scale = 3)
    private BigDecimal selfConsumptionKwh;

    /** Percentual do consumo suprido pela geração própria. */
    @Column(name = "self_sufficiency_pct", precision = 5, scale = 2)
    private BigDecimal selfSufficiencyPct;

    /** Economia no dia, na moeda da planta. */
    @Column(name = "savings", precision = 12, scale = 2)
    private BigDecimal savings;

    @Column(name = "co2_avoided_kg", precision = 10, scale = 3)
    private BigDecimal co2AvoidedKg;
}
