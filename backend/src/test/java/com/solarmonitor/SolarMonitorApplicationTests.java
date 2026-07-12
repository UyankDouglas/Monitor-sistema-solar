package com.solarmonitor;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Teste de integração de fumaça: sobe um PostgreSQL/TimescaleDB real via
 * Testcontainers, aplica as migrations Flyway e valida que o contexto Spring
 * carrega. Scheduler desligado para o teste ser determinístico — a ingestão
 * tem sua própria suíte ({@code IngestionServiceIntegrationTest}).
 */
@Testcontainers
@SpringBootTest(properties = "app.scheduler.enabled=false")
class SolarMonitorApplicationTests {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("timescale/timescaledb:2.17.2-pg16")
                    .asCompatibleSubstituteFor("postgres"));

    @Test
    void contextLoads() {
        // Falha se a aplicação não subir ou as migrations não aplicarem.
    }
}
