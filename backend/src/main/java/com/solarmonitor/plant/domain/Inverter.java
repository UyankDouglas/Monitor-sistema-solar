package com.solarmonitor.plant.domain;

import com.solarmonitor.common.domain.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/** Inversor solar (ex.: Deye SUN-10K-SG04LP3) pertencente a uma planta. */
@Entity
@Table(name = "inverters")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Inverter extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plant_id", nullable = false)
    private Plant plant;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "serial_number", nullable = false, length = 50, unique = true)
    private String serialNumber;

    @Column(name = "model", length = 50)
    private String model;

    /** Potência nominal em watts (SUN-10K = 10.000 W). */
    @Column(name = "rated_power_w")
    private Integer ratedPowerW;

    @Column(name = "phases", nullable = false)
    @Builder.Default
    private Short phases = 3;

    @Column(name = "mppt_count", nullable = false)
    @Builder.Default
    private Short mpptCount = 2;

    @Column(name = "firmware_version", length = 50)
    private String firmwareVersion;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider_type", nullable = false, length = 10)
    @Builder.Default
    private ProviderType providerType = ProviderType.CLOUD;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private InverterStatus status = InverterStatus.UNKNOWN;

    /** Última vez em que uma leitura foi obtida com sucesso (base do alerta de offline). */
    @Column(name = "last_seen_at")
    private Instant lastSeenAt;
}
