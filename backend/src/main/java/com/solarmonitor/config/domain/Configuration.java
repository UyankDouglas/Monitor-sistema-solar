package com.solarmonitor.config.domain;

import com.solarmonitor.common.domain.AuditableEntity;
import com.solarmonitor.plant.domain.Inverter;
import com.solarmonitor.plant.domain.Plant;
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

/**
 * Configuração chave/valor tipada com escopo. As chaves globais padrão são
 * seedadas na migration V8 (intervalo de leitura, tarifa, credenciais do
 * provider etc.). A coerência escopo × referências é garantida por CHECK no
 * banco ({@code chk_config_scope_refs}).
 */
@Entity
@Table(name = "configurations")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Configuration extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "scope", nullable = false, length = 10)
    private ConfigScope scope;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "plant_id")
    private Plant plant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "inverter_id")
    private Inverter inverter;

    @Column(name = "cfg_key", nullable = false, length = 100)
    private String key;

    @Column(name = "cfg_value", nullable = false, length = 500)
    private String value;

    @Enumerated(EnumType.STRING)
    @Column(name = "value_type", nullable = false, length = 10)
    @Builder.Default
    private ConfigValueType valueType = ConfigValueType.STRING;
}
