-- =====================================================================
-- V4 · Telemetria (séries temporais TimescaleDB)
-- energy_sample e mppt_reading como hypertables particionadas por tempo.
--
-- Decisões importantes:
--  * PK composta (inverter_id, sampled_at): o TimescaleDB exige que a
--    coluna de particionamento faça parte de qualquer constraint UNIQUE.
--  * mppt_reading NÃO tem FK para energy_sample: o TimescaleDB não
--    permite foreign keys que REFERENCIEM uma hypertable. A associação
--    é lógica, pela chave (inverter_id, sampled_at).
--  * Compressão automática de chunks com mais de 30 dias (as amostras
--    são append-only; nunca sofrem UPDATE).
-- =====================================================================

CREATE TABLE energy_sample (
    inverter_id            BIGINT        NOT NULL REFERENCES inverters (id) ON DELETE CASCADE,
    sampled_at             TIMESTAMPTZ   NOT NULL,
    -- Potências (W)
    ac_power_w             INTEGER,
    load_power_w           INTEGER,
    export_power_w         INTEGER,
    import_power_w         INTEGER,
    battery_power_w        INTEGER,
    -- Energias acumuladas (kWh)
    daily_energy_kwh       NUMERIC(10, 3),
    monthly_energy_kwh     NUMERIC(12, 3),
    total_energy_kwh       NUMERIC(14, 3),
    -- Rede
    grid_voltage_l1        NUMERIC(6, 1),
    grid_voltage_l2        NUMERIC(6, 1),
    grid_voltage_l3        NUMERIC(6, 1),
    grid_current_a         NUMERIC(7, 2),
    grid_frequency_hz      NUMERIC(5, 2),
    -- Bateria
    battery_voltage        NUMERIC(6, 1),
    battery_current_a      NUMERIC(7, 2),
    battery_soc_pct        NUMERIC(5, 2),
    battery_temperature_c  NUMERIC(5, 1),
    -- Inversor
    inverter_temperature_c NUMERIC(5, 1),
    inverter_status        VARCHAR(20) NOT NULL DEFAULT 'UNKNOWN',
    PRIMARY KEY (inverter_id, sampled_at),
    CONSTRAINT chk_sample_status CHECK (inverter_status IN ('ONLINE', 'OFFLINE', 'FAULT', 'STANDBY', 'UNKNOWN'))
);

-- create_default_indexes => FALSE: o índice default (sampled_at DESC) seria
-- idêntico ao idx_energy_sample_time criado logo abaixo — evita duplicidade.
SELECT create_hypertable('energy_sample', by_range('sampled_at', INTERVAL '7 days'),
                         create_default_indexes => FALSE);

-- Consultas de dashboard varrem por tempo em todos os inversores.
CREATE INDEX idx_energy_sample_time ON energy_sample (sampled_at DESC);

ALTER TABLE energy_sample
    SET (timescaledb.compress,
        timescaledb.compress_segmentby = 'inverter_id',
        timescaledb.compress_orderby = 'sampled_at DESC');

SELECT add_compression_policy('energy_sample', INTERVAL '30 days');

-- ---------------------------------------------------------------------
-- Leituras por MPPT (o SUN-10K-SG04LP3 tem 2 strings; modelado 1..N
-- para suportar outros inversores no futuro).
-- ---------------------------------------------------------------------
CREATE TABLE mppt_reading (
    inverter_id  BIGINT      NOT NULL REFERENCES inverters (id) ON DELETE CASCADE,
    sampled_at   TIMESTAMPTZ NOT NULL,
    string_index SMALLINT    NOT NULL,
    voltage      NUMERIC(6, 1),
    current_a    NUMERIC(7, 2),
    power_w      INTEGER,
    PRIMARY KEY (inverter_id, sampled_at, string_index),
    CONSTRAINT chk_mppt_string_index CHECK (string_index >= 1)
);

SELECT create_hypertable('mppt_reading', by_range('sampled_at', INTERVAL '7 days'));

ALTER TABLE mppt_reading
    SET (timescaledb.compress,
        timescaledb.compress_segmentby = 'inverter_id',
        timescaledb.compress_orderby = 'sampled_at DESC');

SELECT add_compression_policy('mppt_reading', INTERVAL '30 days');
