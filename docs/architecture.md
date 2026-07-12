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
- **`EnergyProvider`**: troca nuvem ↔ local ↔ simulado sem tocar no restante do sistema. Modelo neutro `EnergyReading` (record imutável). Roteamento pela config `provider.mode` (editável em runtime); implementações em `provider/cloud`, `provider/local` e `provider/sim` (Etapa 4).
- **Modo local**: protocolo Solarman V5 (encapsula Modbus RTU) sobre TCP 8899; mapa de registradores do SUN-10K-SG04LP3 concentrado em `DeyeRegisters` (calibrável contra o equipamento real).
- **Modo simulado**: curva solar sintética (padrão de fábrica desde a V9) — dashboard e histórico funcionam sem hardware/credenciais.
- **Resiliência**: timeouts de socket/HTTP + contagem de falhas consecutivas; 3 falhas seguidas marcam o inversor `OFFLINE` (alerta na etapa de alertas). Circuit breaker formal (Resilience4j) avaliado se o campo mostrar necessidade.
- **Séries temporais**: TimescaleDB (hypertables + agregados contínuos + compressão). `energy_sample` de alta frequência + rollups `daily/monthly_generation`.
- **Tempo real**: `IngestionService` persiste (transação em `IngestionPersister`) e publica via `RealtimePublisher` fora da transação — broker indisponível não desfaz amostra. Tópicos STOMP: `/topic/readings` e `/topic/inverters/{id}/readings`; endpoint `/ws`.
- **Scheduler dinâmico**: cada ciclo agenda o próximo lendo `scheduler.reading-interval-ms` do banco (padrão 5 s) — mudanças valem no ciclo seguinte, sem restart (`IngestionScheduler`, `SmartLifecycle`).
- **Segurança**: JWT de acesso + refresh token rotativo; papéis ADMIN/USER (Etapa 6).
- **Contratos**: DTOs + MapStruct + Bean Validation; entidades JPA não vazam para a web.
- **Erros/observabilidade**: Problem Details (RFC 7807), logs JSON, Actuator/Micrometer.
- **Multi-inversor**: tudo chaveado por `inverter_id`, agregações por `plant`.

## Roadmap de etapas
E2 bootstrap · E3 domínio+migrations · E4 EnergyProvider+scheduler · E5 telemetria/agregação/economia/estatística/alertas · E6 segurança · E7 WebSocket · E8 Swagger/exceções/logs/testes · E9 frontend scaffold · E10 dashboard · E11 histórico/config/alertas UI · E12 extras (clima, PWA, i18n, notificações) · E13 docs/hardening.
