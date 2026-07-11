-- =====================================================================
-- V8 · Seeds padrão
-- Planta e inversor iniciais (Deye SUN-10K-SG04LP3) + configurações
-- globais default. Tudo editável depois pela tela de Configurações.
-- =====================================================================

INSERT INTO plants (name, timezone, currency, kwh_price, co2_factor_kg_per_kwh)
VALUES ('Minha Usina', 'America/Sao_Paulo', 'BRL', 0.95, 0.0817);

-- Serial "CONFIGURAR-SN" é placeholder: o usuário informa o serial real
-- do logger Solarman na tela de Configurações antes da primeira coleta.
INSERT INTO inverters (plant_id, name, serial_number, model, rated_power_w, phases, mppt_count, provider_type)
SELECT p.id, 'Deye SUN-10K-SG04LP3', 'CONFIGURAR-SN', 'SUN-10K-SG04LP3', 10000, 3, 2, 'CLOUD'
FROM plants p
WHERE p.name = 'Minha Usina';

INSERT INTO configurations (scope, cfg_key, cfg_value, value_type)
VALUES ('GLOBAL', 'scheduler.reading-interval-ms',   '5000',              'INT'),
       ('GLOBAL', 'energy.kwh-price',                '0.95',              'DECIMAL'),
       ('GLOBAL', 'energy.currency',                 'BRL',               'STRING'),
       ('GLOBAL', 'energy.co2-factor-kg-per-kwh',    '0.0817',            'DECIMAL'),
       ('GLOBAL', 'app.timezone',                    'America/Sao_Paulo', 'STRING'),
       ('GLOBAL', 'provider.mode',                   'CLOUD',             'STRING'),
       ('GLOBAL', 'provider.local.logger-ip',        '',                  'STRING'),
       ('GLOBAL', 'provider.local.logger-port',      '8899',              'INT'),
       ('GLOBAL', 'provider.cloud.app-id',           '',                  'STRING'),
       ('GLOBAL', 'provider.cloud.app-secret',       '',                  'STRING'),
       ('GLOBAL', 'provider.cloud.email',            '',                  'STRING'),
       ('GLOBAL', 'provider.cloud.password-sha256',  '',                  'STRING');
