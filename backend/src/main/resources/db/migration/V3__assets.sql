-- =====================================================================
-- V3 · Ativos / topologia
-- plants, inverters, devices, configurations.
-- =====================================================================

CREATE TABLE plants (
    id                     BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name                   VARCHAR(100)  NOT NULL,
    address                VARCHAR(255),
    latitude               NUMERIC(9, 6),
    longitude              NUMERIC(9, 6),
    timezone               VARCHAR(50)   NOT NULL DEFAULT 'America/Sao_Paulo',
    installed_capacity_kwp NUMERIC(8, 3),
    currency               VARCHAR(3)    NOT NULL DEFAULT 'BRL',
    kwh_price              NUMERIC(10, 4) NOT NULL DEFAULT 0.95,
    co2_factor_kg_per_kwh  NUMERIC(8, 5) NOT NULL DEFAULT 0.0817,
    created_at             TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at             TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE TABLE inverters (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    plant_id         BIGINT      NOT NULL REFERENCES plants (id) ON DELETE CASCADE,
    name             VARCHAR(100) NOT NULL,
    serial_number    VARCHAR(50) NOT NULL,
    model            VARCHAR(50),
    rated_power_w    INTEGER,
    phases           SMALLINT    NOT NULL DEFAULT 3,
    mppt_count       SMALLINT    NOT NULL DEFAULT 2,
    firmware_version VARCHAR(50),
    provider_type    VARCHAR(10) NOT NULL DEFAULT 'CLOUD',
    status           VARCHAR(20) NOT NULL DEFAULT 'UNKNOWN',
    last_seen_at     TIMESTAMPTZ,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_inverters_serial     UNIQUE (serial_number),
    CONSTRAINT chk_inverters_provider  CHECK (provider_type IN ('CLOUD', 'LOCAL')),
    CONSTRAINT chk_inverters_status    CHECK (status IN ('ONLINE', 'OFFLINE', 'FAULT', 'STANDBY', 'UNKNOWN'))
);

CREATE INDEX idx_inverters_plant ON inverters (plant_id);

CREATE TABLE devices (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    inverter_id   BIGINT      NOT NULL REFERENCES inverters (id) ON DELETE CASCADE,
    type          VARCHAR(10) NOT NULL,
    serial_number VARCHAR(50),
    ip_address    VARCHAR(45),
    port          INTEGER,
    metadata      JSONB,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_devices_type CHECK (type IN ('LOGGER', 'METER', 'BATTERY', 'SENSOR'))
);

CREATE INDEX idx_devices_inverter ON devices (inverter_id);

-- Chave/valor tipado com escopo (GLOBAL, por planta ou por inversor).
-- Colunas cfg_key/cfg_value para evitar colisão com palavras reservadas.
CREATE TABLE configurations (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    scope       VARCHAR(10)  NOT NULL,
    plant_id    BIGINT REFERENCES plants (id) ON DELETE CASCADE,
    inverter_id BIGINT REFERENCES inverters (id) ON DELETE CASCADE,
    cfg_key     VARCHAR(100) NOT NULL,
    cfg_value   VARCHAR(500) NOT NULL,
    value_type  VARCHAR(10)  NOT NULL DEFAULT 'STRING',
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT chk_config_scope      CHECK (scope IN ('GLOBAL', 'PLANT', 'INVERTER')),
    CONSTRAINT chk_config_value_type CHECK (value_type IN ('STRING', 'INT', 'DECIMAL', 'BOOLEAN', 'JSON')),
    CONSTRAINT chk_config_scope_refs CHECK (
        (scope = 'GLOBAL' AND plant_id IS NULL AND inverter_id IS NULL) OR
        (scope = 'PLANT' AND plant_id IS NOT NULL AND inverter_id IS NULL) OR
        (scope = 'INVERTER' AND inverter_id IS NOT NULL)
    )
);

-- Unicidade da chave por escopo (índices parciais).
CREATE UNIQUE INDEX uq_config_global   ON configurations (cfg_key) WHERE scope = 'GLOBAL';
CREATE UNIQUE INDEX uq_config_plant    ON configurations (plant_id, cfg_key) WHERE scope = 'PLANT';
CREATE UNIQUE INDEX uq_config_inverter ON configurations (inverter_id, cfg_key) WHERE scope = 'INVERTER';
