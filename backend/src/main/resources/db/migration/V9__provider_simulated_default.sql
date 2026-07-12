-- =====================================================================
-- V9 · Modo simulado como padrão de fábrica + serial do logger
--
-- A Etapa 4 introduz o provider SIMULATED (curva solar sintética): permite
-- usar dashboard/histórico imediatamente, sem credenciais da nuvem nem
-- hardware acessível. Quem já tiver trocado provider.mode manualmente não é
-- afetado (o UPDATE só alcança o valor de fábrica 'CLOUD' intocado).
--
-- V8 não pode ser editada: já foi aplicada em bancos existentes e o Flyway
-- validaria checksum divergente.
-- =====================================================================

UPDATE configurations
SET cfg_value  = 'SIMULATED',
    updated_at = now()
WHERE scope = 'GLOBAL'
  AND cfg_key = 'provider.mode'
  AND cfg_value = 'CLOUD';

-- Serial numérico do stick logger (etiqueta do equipamento), exigido pelo
-- protocolo V5 do modo LOCAL.
INSERT INTO configurations (scope, cfg_key, cfg_value, value_type)
VALUES ('GLOBAL', 'provider.local.logger-serial', '', 'INT');
