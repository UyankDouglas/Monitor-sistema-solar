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

/** Consolidação mensal por inversor. Uma linha por (inversor, ano, mês). */
@Entity
@Table(name = "monthly_generation",
        uniqueConstraints = @UniqueConstraint(name = "uq_monthly_generation",
                columnNames = {"inverter_id", "year", "month"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MonthlyGeneration extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "inverter_id", nullable = false)
    private Inverter inverter;

    @Column(name = "year", nullable = false)
    private Short year;

    /** Mês de 1 a 12 (CHECK no banco). */
    @Column(name = "month", nullable = false)
    private Short month;

    @Column(name = "energy_kwh", nullable = false, precision = 12, scale = 3)
    @Builder.Default
    private BigDecimal energyKwh = BigDecimal.ZERO;

    @Column(name = "consumption_kwh", precision = 12, scale = 3)
    private BigDecimal consumptionKwh;

    @Column(name = "export_kwh", precision = 12, scale = 3)
    private BigDecimal exportKwh;

    @Column(name = "import_kwh", precision = 12, scale = 3)
    private BigDecimal importKwh;

    @Column(name = "savings", precision = 14, scale = 2)
    private BigDecimal savings;

    @Column(name = "co2_avoided_kg", precision = 12, scale = 3)
    private BigDecimal co2AvoidedKg;
}
