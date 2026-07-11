package com.solarmonitor.energy.domain;

import com.solarmonitor.plant.domain.Inverter;
import com.solarmonitor.plant.domain.InverterStatus;
import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
 * Uma leitura completa do inversor (hypertable TimescaleDB, append-only).
 * Concentra potências, energias acumuladas, rede, bateria e temperatura —
 * uma linha por polling do scheduler (padrão: a cada 5 s).
 *
 * <p>As leituras por MPPT ficam em {@link MpptReading}, associadas
 * logicamente pela mesma chave (inversor, instante) — sem FK física, pois o
 * TimescaleDB não permite foreign keys apontando para hypertables.</p>
 *
 * <p>Implementa {@link Persistable} com {@code isNew()} sempre {@code true}:
 * telemetria é imutável/append-only, então {@code save()} deve sempre fazer
 * INSERT direto (persist), nunca o SELECT+merge que o Spring Data faria para
 * entidades com id atribuído. Re-salvar a mesma chave lança violação de PK —
 * comportamento desejado.</p>
 */
@Entity
@Table(name = "energy_sample")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EnergySample implements Persistable<EnergySampleId> {

    @EmbeddedId
    private EnergySampleId id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("inverterId")
    @JoinColumn(name = "inverter_id", nullable = false)
    private Inverter inverter;

    // --- Potências (W) -------------------------------------------------
    /** Potência AC gerada pelo inversor. */
    @Column(name = "ac_power_w")
    private Integer acPowerW;

    /** Potência consumida pela casa. */
    @Column(name = "load_power_w")
    private Integer loadPowerW;

    /** Potência exportada para a rede. */
    @Column(name = "export_power_w")
    private Integer exportPowerW;

    /** Potência importada da rede. */
    @Column(name = "import_power_w")
    private Integer importPowerW;

    /** Potência da bateria (positivo = descarregando, negativo = carregando). */
    @Column(name = "battery_power_w")
    private Integer batteryPowerW;

    // --- Energias acumuladas (kWh) --------------------------------------
    @Column(name = "daily_energy_kwh", precision = 10, scale = 3)
    private BigDecimal dailyEnergyKwh;

    @Column(name = "monthly_energy_kwh", precision = 12, scale = 3)
    private BigDecimal monthlyEnergyKwh;

    @Column(name = "total_energy_kwh", precision = 14, scale = 3)
    private BigDecimal totalEnergyKwh;

    // --- Rede ------------------------------------------------------------
    @Column(name = "grid_voltage_l1", precision = 6, scale = 1)
    private BigDecimal gridVoltageL1;

    @Column(name = "grid_voltage_l2", precision = 6, scale = 1)
    private BigDecimal gridVoltageL2;

    @Column(name = "grid_voltage_l3", precision = 6, scale = 1)
    private BigDecimal gridVoltageL3;

    @Column(name = "grid_current_a", precision = 7, scale = 2)
    private BigDecimal gridCurrentA;

    @Column(name = "grid_frequency_hz", precision = 5, scale = 2)
    private BigDecimal gridFrequencyHz;

    // --- Bateria ----------------------------------------------------------
    @Column(name = "battery_voltage", precision = 6, scale = 1)
    private BigDecimal batteryVoltage;

    @Column(name = "battery_current_a", precision = 7, scale = 2)
    private BigDecimal batteryCurrentA;

    /** Estado de carga da bateria (0–100%). */
    @Column(name = "battery_soc_pct", precision = 5, scale = 2)
    private BigDecimal batterySocPct;

    @Column(name = "battery_temperature_c", precision = 5, scale = 1)
    private BigDecimal batteryTemperatureC;

    // --- Inversor ----------------------------------------------------------
    @Column(name = "inverter_temperature_c", precision = 5, scale = 1)
    private BigDecimal inverterTemperatureC;

    @Enumerated(EnumType.STRING)
    @Column(name = "inverter_status", nullable = false, length = 20)
    @Builder.Default
    private InverterStatus inverterStatus = InverterStatus.UNKNOWN;

    @Override
    @Transient
    public boolean isNew() {
        return true;
    }
}
