# Arquitetura — Monitor Solar Deye

## Estilo
Clean Architecture pragmática + organização **por feature** (package-by-feature).
**Hexagonal (Ports & Adapters)** aplicado na camada de integração com o inversor.
DDD tático apenas onde agrega valor.

## Camadas (fluxo de dados)
```
Equipamento          Inversor Deye SUN-10K-SG04LP3  ──►  Logger Solarman (nuvem / TCP 8899)
Integração           EnergyProvider (port)
                       ├─ SolarmanCloudProvider (API REST oficial)
                       └─ SolarmanLocalProvider (Modbus V5 · porta 8899)
Backend              Scheduler(5s) ─► Serviços de Domínio ─► PostgreSQL/TimescaleDB
                                          └─ evento ─► WebSocket (tempo real)
Exposição            REST API (OpenAPI/Swagger) + WebSocket (STOMP)
Frontend             SPA React (Dashboard · Histórico · Alertas · Configurações)
```

## Decisões-chave
- **`EnergyProvider`**: troca nuvem ↔ local sem tocar no restante do sistema. Modelo neutro `InverterReading`.
- **Modo local**: protocolo Solarman V5 (encapsula Modbus RTU) sobre TCP 8899.
- **Resiliência**: Resilience4j (timeout/retry/circuit breaker); falha repetida marca inversor `OFFLINE` e gera alerta.
- **Séries temporais**: TimescaleDB (hypertables + agregados contínuos + compressão). `energy_sample` de alta frequência + rollups `daily/monthly_generation`.
- **Tempo real**: scheduler grava e publica evento; listener transmite via WebSocket STOMP (tópico por inversor).
- **Scheduler dinâmico**: intervalo reconfigurável em runtime (padrão 5s).
- **Segurança**: JWT de acesso + refresh token rotativo; papéis ADMIN/USER (Etapa 6).
- **Contratos**: DTOs + MapStruct + Bean Validation; entidades JPA não vazam para a web.
- **Erros/observabilidade**: Problem Details (RFC 7807), logs JSON, Actuator/Micrometer.
- **Multi-inversor**: tudo chaveado por `inverter_id`, agregações por `plant`.

## Roadmap de etapas
E2 bootstrap · E3 domínio+migrations · E4 EnergyProvider+scheduler · E5 telemetria/agregação/economia/estatística/alertas · E6 segurança · E7 WebSocket · E8 Swagger/exceções/logs/testes · E9 frontend scaffold · E10 dashboard · E11 histórico/config/alertas UI · E12 extras (clima, PWA, i18n, notificações) · E13 docs/hardening.
