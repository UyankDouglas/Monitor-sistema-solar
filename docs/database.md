# Modelo de dados — Monitor Solar Deye

> Schema implementado nas migrations Flyway V1–V8 (Etapas 2 e 3). As entidades JPA
> vivem em `backend/src/main/java/com/solarmonitor/<contexto>/domain` e são validadas
> contra o schema em todo start (`ddl-auto: validate`) e nos testes de repositório.
>
> **Decisões do TimescaleDB** (detalhes nos comentários da `V4__telemetry.sql`):
> - `energy_sample` e `mppt_reading` são hypertables com chunks de 7 dias e
>   compressão automática após 30 dias;
> - a PK inclui `sampled_at` (exigência do Timescale para constraints UNIQUE);
> - `mppt_reading` não tem FK física para `energy_sample` (FKs apontando para
>   hypertable não são suportadas) — a associação é lógica por (inverter_id, sampled_at).

## Contextos e entidades

### Identidade & Acesso
- `users` — id, username, email, password_hash, full_name, enabled, locale, theme, timestamps
- `roles` — id, name (`ADMIN` | `USER`)
- `user_roles` — user_id, role_id (N:N)
- `refresh_tokens` — id, user_id→users, token_hash, expires_at, revoked, user_agent, ip

### Ativos / topologia
- `plants` — id, name, address, latitude, longitude, timezone, installed_capacity_kwp, currency, kwh_price, co2_factor_kg_per_kwh
- `inverters` — id, plant_id→plants, name, serial_number, model, rated_power_w, phases, mppt_count, firmware, provider_type (`CLOUD`|`LOCAL`), status, last_seen_at
- `devices` — id, inverter_id→inverters, type (`LOGGER`|`METER`|`BATTERY`|`SENSOR`), serial_number, ip_address, port, metadata (jsonb)
- `configurations` — id, scope (`GLOBAL`|`PLANT`|`INVERTER`), plant_id?, inverter_id?, key, value, value_type

### Telemetria (séries temporais)
- `energy_sample` — **hypertable** por `sampled_at`: inverter_id→inverters, ac_power_w, daily/monthly/total_energy_kwh, load/export/import_power_w, grid_voltage_l1/l2/l3, grid_current, grid_frequency, battery_power_w, battery_voltage, battery_current, battery_soc, battery_temperature_c, inverter_temperature_c, status
- `mppt_reading` — energy_sample_id→energy_sample, string_index, voltage, current, power (1..N por amostra; 2 no SUN-10K)

> Valores de bateria e rede de alta frequência ficam embutidos em `energy_sample`
> (uma leitura = tudo). `BatteryStatus`/`GridStatus` do enunciado ficam representados
> por essas colunas; snapshots de saúde da bateria (SOH, ciclos) podem virar tabela
> dedicada de baixa frequência se necessário.

### Agregações
- `daily_generation` — inverter_id, date, energy_kwh, peak_power_w, peak_at, min_power_w, consumption/export/import_kwh, self_consumption_kwh, self_sufficiency_pct, savings, co2_avoided_kg — UNIQUE(inverter_id, date)
- `monthly_generation` — inverter_id, year, month, energy_kwh, savings, co2_avoided_kg, consumption/export/import_kwh — UNIQUE(inverter_id, year, month)

### Alertas & Clima
- `alerts` — id, inverter_id, type, severity, status (`ACTIVE`|`ACKNOWLEDGED`|`RESOLVED`), message, details (jsonb), triggered_at, resolved_at
- `alert_rules` — id, type (UNIQUE), enabled, threshold (jsonb) — globais por ora; escopo por planta/inversor virá em migration futura com colunas de referência
- `weather` — id, plant_id, observed_at, temperature_c, condition, cloud_cover_pct, irradiance_w_m2, is_forecast, expected_generation_kwh

## Plano de migrations Flyway
| Versão | Conteúdo |
|--------|----------|
| V1 | baseline: extensão TimescaleDB + `app_info` (**Etapa 2**) |
| V2 | identidade: users, roles, user_roles, refresh_tokens (+seed roles/admin) |
| V3 | ativos: plants, inverters, devices, configurations |
| V4 | telemetria: energy_sample (hypertable) + mppt_reading + índices |
| V5 | agregações: daily_generation, monthly_generation |
| V6 | alertas: alerts, alert_rules |
| V7 | clima: weather |
| V8 | seeds default: intervalo 5s, kWh, moeda, timezone |
