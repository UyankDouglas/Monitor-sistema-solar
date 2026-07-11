-- =====================================================================
-- V1 · Baseline do schema
-- Habilita a extensão TimescaleDB (usada para as hypertables de
-- telemetria a partir da Etapa 3) e registra metadados da aplicação.
-- As tabelas de domínio (plants, inverters, energy_sample, ...) serão
-- criadas na Etapa 3.
-- =====================================================================

CREATE EXTENSION IF NOT EXISTS timescaledb CASCADE;

CREATE TABLE app_info (
    id          SMALLINT PRIMARY KEY DEFAULT 1,
    app_name    VARCHAR(100) NOT NULL,
    schema_note VARCHAR(255) NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT app_info_singleton CHECK (id = 1)
);

INSERT INTO app_info (id, app_name, schema_note)
VALUES (1, 'monitor-solar-deye', 'baseline criado na Etapa 2; domínio na Etapa 3');
