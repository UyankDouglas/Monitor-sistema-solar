package com.solarmonitor.plant.domain;

import com.solarmonitor.common.domain.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Usina/planta solar. Agrupa inversores e concentra parâmetros econômicos
 * (tarifa do kWh, moeda, fator de CO₂) e de localização (para clima).
 */
@Entity
@Table(name = "plants")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Plant extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "address", length = 255)
    private String address;

    @Column(name = "latitude", precision = 9, scale = 6)
    private BigDecimal latitude;

    @Column(name = "longitude", precision = 9, scale = 6)
    private BigDecimal longitude;

    @Column(name = "timezone", nullable = false, length = 50)
    @Builder.Default
    private String timezone = "America/Sao_Paulo";

    @Column(name = "installed_capacity_kwp", precision = 8, scale = 3)
    private BigDecimal installedCapacityKwp;

    @Column(name = "currency", nullable = false, length = 3)
    @Builder.Default
    private String currency = "BRL";

    @Column(name = "kwh_price", nullable = false, precision = 10, scale = 4)
    @Builder.Default
    private BigDecimal kwhPrice = new BigDecimal("0.95");

    @Column(name = "co2_factor_kg_per_kwh", nullable = false, precision = 8, scale = 5)
    @Builder.Default
    private BigDecimal co2FactorKgPerKwh = new BigDecimal("0.0817");
}
