package com.solarmonitor.alert.domain;

import com.solarmonitor.common.domain.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.Map;

/**
 * Regra configurável de disparo de alerta. Os limiares ficam em
 * {@code threshold} (JSONB) por serem heterogêneos entre tipos — ex.:
 * {@code {"min_soc_pct": 15}} para LOW_BATTERY. Regras padrão são seedadas
 * na migration V6.
 *
 * <p>Regras são globais por ora; escopo por planta/inversor entra em migration
 * futura junto com as colunas de referência necessárias.</p>
 */
@Entity
@Table(name = "alert_rules")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AlertRule extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 40, unique = true)
    private AlertType type;

    @Column(name = "enabled", nullable = false)
    @Builder.Default
    private boolean enabled = true;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "threshold")
    private Map<String, Object> threshold;
}
