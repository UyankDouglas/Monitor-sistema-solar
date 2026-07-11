-- =====================================================================
-- V5 · Agregações diárias e mensais
-- Consolidadas pelo scheduler a partir de energy_sample; alimentam os
-- gráficos de geração diária/mensal/anual e as estatísticas.
-- =====================================================================

CREATE TABLE daily_generation (
    id                   BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    inverter_id          BIGINT       NOT NULL REFERENCES inverters (id) ON DELETE CASCADE,
    generation_date      DATE         NOT NULL,
    energy_kwh           NUMERIC(10, 3) NOT NULL DEFAULT 0,
    peak_power_w         INTEGER,
    peak_at              TIMESTAMPTZ,
    min_power_w          INTEGER,
    consumption_kwh      NUMERIC(10, 3),
    export_kwh           NUMERIC(10, 3),
    import_kwh           NUMERIC(10, 3),
    self_consumption_kwh NUMERIC(10, 3),
    self_sufficiency_pct NUMERIC(5, 2),
    savings              NUMERIC(12, 2),
    co2_avoided_kg       NUMERIC(10, 3),
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_daily_generation UNIQUE (inverter_id, generation_date)
);

CREATE INDEX idx_daily_generation_date ON daily_generation (generation_date DESC);

CREATE TABLE monthly_generation (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    inverter_id     BIGINT   NOT NULL REFERENCES inverters (id) ON DELETE CASCADE,
    year            SMALLINT NOT NULL,
    month           SMALLINT NOT NULL,
    energy_kwh      NUMERIC(12, 3) NOT NULL DEFAULT 0,
    consumption_kwh NUMERIC(12, 3),
    export_kwh      NUMERIC(12, 3),
    import_kwh      NUMERIC(12, 3),
    savings         NUMERIC(14, 2),
    co2_avoided_kg  NUMERIC(12, 3),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_monthly_generation  UNIQUE (inverter_id, year, month),
    CONSTRAINT chk_monthly_month      CHECK (month BETWEEN 1 AND 12),
    CONSTRAINT chk_monthly_year       CHECK (year BETWEEN 2000 AND 2200)
);
