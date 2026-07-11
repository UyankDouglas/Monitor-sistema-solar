package com.solarmonitor.weather.domain;

import com.solarmonitor.plant.domain.Plant;
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
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Registro meteorológico de uma planta — observação ({@code isForecast=false})
 * ou previsão ({@code isForecast=true}). Base da comparação geração prevista ×
 * real.
 */
@Entity
@Table(name = "weather",
        uniqueConstraints = @UniqueConstraint(name = "uq_weather_plant_time",
                columnNames = {"plant_id", "observed_at", "is_forecast"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Weather {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plant_id", nullable = false)
    private Plant plant;

    @Column(name = "observed_at", nullable = false)
    private Instant observedAt;

    @Column(name = "temperature_c", precision = 5, scale = 1)
    private BigDecimal temperatureC;

    /** Condição textual normalizada (ex.: "clear", "clouds", "rain"). */
    @Column(name = "condition", length = 50)
    private String condition;

    @Column(name = "cloud_cover_pct", precision = 5, scale = 2)
    private BigDecimal cloudCoverPct;

    @Column(name = "irradiance_w_m2", precision = 7, scale = 1)
    private BigDecimal irradianceWM2;

    @Column(name = "is_forecast", nullable = false)
    @Builder.Default
    private boolean forecast = false;

    /** Geração esperada estimada a partir da previsão (kWh). */
    @Column(name = "expected_generation_kwh", precision = 10, scale = 3)
    private BigDecimal expectedGenerationKwh;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
