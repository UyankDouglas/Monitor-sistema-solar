-- =====================================================================
-- V12 · Configuração do clima (Open-Meteo — gratuito, sem chave de API)
-- Latitude/longitude da usina habilitam previsão × geração real.
-- =====================================================================

INSERT INTO configurations (scope, cfg_key, cfg_value, value_type)
VALUES ('GLOBAL', 'weather.enabled', 'true', 'BOOLEAN'),
       ('GLOBAL', 'weather.latitude', '', 'DECIMAL'),
       ('GLOBAL', 'weather.longitude', '', 'DECIMAL');
