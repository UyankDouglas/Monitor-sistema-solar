# ☀️ Monitor Solar Deye

Plataforma completa de monitoramento de geração de energia solar para o
inversor **Deye SUN-10K-SG04LP3** com logger Solarman — substituindo o
aplicativo Solarman Smart por uma solução própria, moderna e sem limites de
histórico.

**Stack:** Java 21 · Spring Boot 3.4 · PostgreSQL + TimescaleDB · Flyway ·
React 18 + TypeScript + Vite · Material UI · ECharts · WebSocket (STOMP) ·
JWT · Docker Compose.

## Funcionalidades

- **Dashboard em tempo real** — potência, energia (hoje/mês/total), economia,
  CO₂ evitado, consumo da casa, exportação/importação, bateria (SOC, potência,
  temperatura), rede por fase, strings MPPT e fluxo de energia; gráfico de
  potência ao vivo via WebSocket (leituras a cada 5 s)
- **Histórico completo** — filtros por dia/semana/mês/ano/período, curva
  intradiária, estatísticas (melhor/pior dia, picos, médias, kWh/kWp, fator de
  capacidade) e exportação **CSV / Excel / PDF**
- **Alertas automáticos** — inversor offline, sem geração no horário solar,
  temperatura alta, bateria baixa, falha do inversor e perda de comunicação;
  com deduplicação, auto-resolução, notificações do navegador e badge ao vivo
- **Clima** — previsão Open-Meteo (gratuita, sem chave) comparada à geração
  real, com estimativa por radiação solar × capacidade instalada
- **Segurança** — login JWT + refresh token rotativo com detecção de reuso,
  papéis ADMIN/USER, troca de senha obrigatória no primeiro acesso
- **Configuração em runtime** — intervalo de leitura, tarifa do kWh, moeda,
  fator CO₂, fuso, credenciais e origem dos dados mudam sem reiniciar
- **PWA** — instalável no celular/desktop
- **3 origens de dados** — API oficial Solarman (CLOUD), logger local na porta
  8899 (LOCAL, protocolo V5/Modbus) e simulador realista (SIMULATED, padrão de
  fábrica) para usar o sistema antes de conectar o equipamento

## Como executar

Pré-requisito: [Docker Desktop](https://www.docker.com/products/docker-desktop/).

```bash
git clone https://github.com/UyankDouglas/Monitor-sistema-solar.git
cd Monitor-sistema-solar/docker
cp .env.example .env          # ajuste JWT_SECRET em produção!
docker compose up -d --build
```

| Serviço | URL |
|---------|-----|
| Aplicação | http://localhost:5173 |
| API + Swagger | http://localhost:8080/swagger-ui.html |
| pgAdmin | http://localhost:5050 (`admin@solar.dev` / `admin`) |

**Primeiro acesso:** `admin` / `admin123` — o sistema exige a troca da senha
antes de liberar qualquer tela.

O sistema nasce no modo **SIMULATED**: uma curva solar realista alimenta o
dashboard imediatamente, sem hardware.

## Conectando o seu inversor real

Tudo em **Configurações** (menu lateral, apenas ADMIN). As mudanças valem no
ciclo seguinte, sem reiniciar.

### Modo CLOUD (API oficial Solarman — recomendado para começar)

1. Solicite credenciais de API em [home.solarmanpv.com](https://home.solarmanpv.com)
   (menu da conta → *API Service*): você receberá **App ID** e **App Secret**
2. Preencha o grupo **Solarman Cloud**: App ID, App Secret, e-mail da conta e
   a senha como **SHA-256 hex** (gere em qualquer ferramenta local; a senha em
   claro nunca é armazenada)
3. Em **Inversores** (`/api/inverters` no Swagger) confirme que o serial do
   inversor está correto — troque o placeholder `CONFIGURAR-SN` pelo serial
   real da etiqueta do equipamento (via banco/pgAdmin nesta versão)
4. Troque **Origem dos dados** para `CLOUD`

### Modo LOCAL (logger na sua rede, sem internet)

1. Descubra o IP do stick logger no seu roteador (reserve um IP fixo)
2. Preencha o grupo **Logger local**: IP, porta (8899) e o **serial numérico**
   do logger (etiqueta do stick)
3. Troque **Origem dos dados** para `LOCAL`

> O mapa de registradores Modbus do SUN-10K-SG04LP3 está em
> `DeyeRegisters.java`, baseado em mapeamentos da comunidade — na primeira
> conexão real, confira os valores contra o display do inversor e ajuste ali
> se necessário (endereços/escalas documentados na classe).

### Clima (opcional)

Em **Configurações → Clima**, informe a latitude/longitude da usina
(ex.: `-23.5505` / `-46.6333`). A página **Clima** passa a comparar a geração
prevista (radiação Open-Meteo × capacidade × PR 0,80) com a real.

## Arquitetura

```
Inversor Deye ──► Logger Solarman ──► EnergyProvider (port)
                                        ├── SolarmanCloudProvider  (API REST oficial)
                                        ├── SolarmanLocalProvider  (TCP 8899 · Solarman V5 + Modbus RTU)
                                        └── SimulatedProvider      (curva solar sintética)
                                                 │
                    IngestionScheduler (5 s, reconfigurável em runtime)
                                                 │
              PostgreSQL + TimescaleDB (hypertables + compressão automática)
                    │                    │                   │
          AggregationService      AlertEngine (60 s)   WeatherService (1 h)
          (diário/mensal, 5 min)        │                   │
                    └────────────► API REST + WebSocket STOMP ◄─┘
                                                 │
                                  React SPA (MUI · ECharts · PWA)
```

Decisões detalhadas em [docs/architecture.md](docs/architecture.md) e modelo
de dados em [docs/database.md](docs/database.md).

## Estrutura

```
backend/   Spring Boot (pacotes por contexto: provider, ingestion, aggregation,
           alert, weather, energy, dashboard, statistics, config, user, security)
frontend/  React + TS (pages, components, layout, auth, realtime, api)
docker/    docker-compose.yml (backend, frontend/nginx, TimescaleDB, pgAdmin)
docs/      arquitetura e modelo de dados
```

## Desenvolvimento e testes

Backend (sem JDK local, via Docker — Testcontainers precisa do socket):

```bash
docker run --rm -e TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal \
  -v "$PWD/backend:/app" -w /app \
  -v /var/run/docker.sock:/var/run/docker.sock \
  -v solar-maven-repo:/root/.m2 \
  maven:3.9-eclipse-temurin-21 \
  sh -c "printf 'api.version=1.44\n' > /root/.docker-java.properties; mvn -B test"
```

Frontend:

```bash
cd frontend && npm install && npm run dev   # dev server com proxy p/ :8080
```

A suíte cobre protocolo Solarman V5/Modbus byte a byte, agregações com valores
derivados à mão, fluxo completo de autenticação (rotação e reuso de refresh
token), motor de alertas e API via MockMvc — tudo contra TimescaleDB real
(Testcontainers).

## Limitações conhecidas / roadmap

- Interface em pt-BR (i18n planejado; ainda não implementado)
- Cadastro/edição de plantas e inversores pela interface (hoje: banco/Swagger)
- Notificações externas (e-mail/Telegram) — hoje: navegador + tempo real na UI
- Mapa de registradores do modo LOCAL requer calibração no equipamento real

## Licença

Projeto pessoal — todos os direitos reservados.
