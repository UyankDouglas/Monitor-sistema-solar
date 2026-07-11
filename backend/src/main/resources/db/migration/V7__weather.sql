-- =====================================================================
-- V7 · Clima
-- Observações e previsões meteorológicas por planta, usadas na
-- comparação geração prevista × geração real.
-- =====================================================================

CREATE TABLE weather (
    id                      BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    plant_id                BIGINT      NOT NULL REFERENCES plants (id) ON DELETE CASCADE,
    observed_at             TIMESTAMPTZ NOT NULL,
    temperature_c           NUMERIC(5, 1),
    condition               VARCHAR(50),
    cloud_cover_pct         NUMERIC(5, 2),
    irradiance_w_m2         NUMERIC(7, 1),
    is_forecast             BOOLEAN     NOT NULL DEFAULT FALSE,
    expected_generation_kwh NUMERIC(10, 3),
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_weather_plant_time UNIQUE (plant_id, observed_at, is_forecast)
);

CREATE INDEX idx_weather_plant_time ON weather (plant_id, observed_at DESC);
