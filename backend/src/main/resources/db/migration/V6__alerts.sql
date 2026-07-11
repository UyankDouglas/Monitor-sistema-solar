-- =====================================================================
-- V6 · Alertas
-- alert_rules define os gatilhos configuráveis; alerts registra as
-- ocorrências e seu ciclo de vida (ACTIVE → ACKNOWLEDGED → RESOLVED).
-- =====================================================================

-- Regras são globais por ora (1 planta / 1 inversor). Escopo por planta ou
-- inversor será introduzido em migration futura, junto com as colunas de
-- referência e a unicidade composta que ele exige.
CREATE TABLE alert_rules (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    type       VARCHAR(40) NOT NULL,
    enabled    BOOLEAN     NOT NULL DEFAULT TRUE,
    threshold  JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_alert_rules_type  UNIQUE (type),
    CONSTRAINT chk_alert_rules_type CHECK (type IN (
        'INVERTER_OFFLINE', 'NO_GENERATION_DAYTIME', 'HIGH_TEMPERATURE',
        'LOW_BATTERY', 'INVERTER_FAULT', 'COMMUNICATION_LOSS'))
);

CREATE TABLE alerts (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    inverter_id     BIGINT       NOT NULL REFERENCES inverters (id) ON DELETE CASCADE,
    type            VARCHAR(40)  NOT NULL,
    severity        VARCHAR(10)  NOT NULL,
    status          VARCHAR(15)  NOT NULL DEFAULT 'ACTIVE',
    message         VARCHAR(255) NOT NULL,
    details         JSONB,
    triggered_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    acknowledged_at TIMESTAMPTZ,
    resolved_at     TIMESTAMPTZ,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT chk_alerts_type CHECK (type IN (
        'INVERTER_OFFLINE', 'NO_GENERATION_DAYTIME', 'HIGH_TEMPERATURE',
        'LOW_BATTERY', 'INVERTER_FAULT', 'COMMUNICATION_LOSS')),
    CONSTRAINT chk_alerts_severity CHECK (severity IN ('INFO', 'WARNING', 'CRITICAL')),
    CONSTRAINT chk_alerts_status   CHECK (status IN ('ACTIVE', 'ACKNOWLEDGED', 'RESOLVED'))
);

CREATE INDEX idx_alerts_inverter_status ON alerts (inverter_id, status);
CREATE INDEX idx_alerts_triggered       ON alerts (triggered_at DESC);
-- Dashboard filtra/conta por status isolado (badge de alertas ativos).
CREATE INDEX idx_alerts_status_triggered ON alerts (status, triggered_at DESC);

-- ---------------------------------------------------------------------
-- Seeds: regras padrão com limiares em JSONB (ajustáveis na tela de
-- configurações). Unidades documentadas dentro do próprio JSON.
-- ---------------------------------------------------------------------
INSERT INTO alert_rules (type, enabled, threshold)
VALUES ('INVERTER_OFFLINE',      TRUE, '{"offline_after_seconds": 120}'),
       ('NO_GENERATION_DAYTIME', TRUE, '{"solar_window_start": "08:00", "solar_window_end": "17:00", "min_power_w": 50, "grace_minutes": 30}'),
       ('HIGH_TEMPERATURE',      TRUE, '{"inverter_max_c": 65, "battery_max_c": 45}'),
       ('LOW_BATTERY',           TRUE, '{"min_soc_pct": 15}'),
       ('INVERTER_FAULT',        TRUE, '{}'),
       ('COMMUNICATION_LOSS',    TRUE, '{"max_failed_polls": 5}');
