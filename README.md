# Monitor Solar Deye

Plataforma de monitoramento de geração de energia solar para o inversor **Deye SUN-10K-SG04LP3**
(logger Solarman), substituindo o app Solarman Smart. Dashboards em tempo real, histórico
completo, alertas, economia e previsão do tempo.

> **Status:** Etapa 2 (bootstrap). Esqueleto executável do backend, frontend e infraestrutura.
> As funcionalidades de negócio chegam nas próximas etapas — veja [docs/architecture.md](docs/architecture.md).

## Tecnologias
- **Backend:** Java 21 · Spring Boot 3 · Spring Web/JPA/Security · Flyway · Lombok · MapStruct · Maven
- **Banco:** PostgreSQL + **TimescaleDB**
- **Frontend:** React · Vite · TypeScript · Material UI · TanStack Query · Axios · Apache ECharts
- **Infra:** Docker · Docker Compose · pgAdmin · Swagger/OpenAPI · WebSocket · Testcontainers

## Estrutura
```
backend/    API Spring Boot (package-by-feature + hexagonal na integração)
frontend/   SPA React + Vite
docker/     docker-compose (backend, frontend, postgres/timescaledb, pgadmin)
docs/       arquitetura e modelo de dados
```

## Como executar (Docker — recomendado)
Requer Docker + Docker Compose.

```bash
cd docker
cp .env.example .env      # ajuste credenciais/portas se quiser
docker compose up --build
```

Serviços:
| Serviço   | URL                              |
|-----------|----------------------------------|
| Frontend  | http://localhost:5173            |
| Backend   | http://localhost:8080/api/ping   |
| Swagger   | http://localhost:8080/swagger-ui.html |
| Health    | http://localhost:8080/actuator/health |
| pgAdmin   | http://localhost:5050            |

## Desenvolvimento local (sem Docker)
**Backend** (precisa de JDK 21 + um PostgreSQL/TimescaleDB rodando):
```bash
cd backend
./mvnw spring-boot:run      # ou: mvn spring-boot:run
```
Configuração via variáveis `SPRING_DATASOURCE_URL/USERNAME/PASSWORD`
(padrão: `jdbc:postgresql://localhost:5432/solar`, user/senha `solar`).

**Frontend** (Node 20+):
```bash
cd frontend
npm install
npm run dev                 # http://localhost:5173 (proxy /api → :8080)
```

## Configuração
- Intervalo de leitura, valor do kWh, moeda e fator de CO₂: `backend/src/main/resources/application.yml` (bloco `app`).
  Passarão a ser editáveis pela tela de configurações (persistidos em `configurations`) nas próximas etapas.

## Conectar ao inversor / trocar modo local ↔ cloud
Implementado na **Etapa 4** via o port `EnergyProvider`
(`SolarmanCloudProvider` e `SolarmanLocalProvider`). A seleção será por inversor,
configurável sem alterar o restante do sistema.

## Testes
```bash
cd backend
mvn test        # inclui teste de integração com Testcontainers (requer Docker)
```
