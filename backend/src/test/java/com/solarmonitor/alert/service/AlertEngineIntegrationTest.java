package com.solarmonitor.alert.service;

import com.solarmonitor.alert.domain.AlertStatus;
import com.solarmonitor.alert.domain.AlertType;
import com.solarmonitor.alert.repository.AlertRepository;
import com.solarmonitor.energy.domain.EnergySample;
import com.solarmonitor.energy.domain.EnergySampleId;
import com.solarmonitor.energy.repository.EnergySampleRepository;
import com.solarmonitor.plant.domain.Inverter;
import com.solarmonitor.plant.domain.InverterStatus;
import com.solarmonitor.plant.repository.InverterRepository;
import com.solarmonitor.realtime.RealtimePublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.EnumSet;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;

/**
 * Ciclo de vida dos alertas contra banco real: disparo, deduplicação e
 * auto-resolução. Cada teste controla o estado do inversor/amostras que a
 * sua regra observa; tipos diferentes não interferem entre si.
 */
@Testcontainers
@SpringBootTest(properties = "app.scheduler.enabled=false")
class AlertEngineIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("timescale/timescaledb:2.17.2-pg16")
                    .asCompatibleSubstituteFor("postgres"));

    @Autowired
    private AlertEngine alertEngine;

    @Autowired
    private AlertRepository alertRepository;

    @Autowired
    private InverterRepository inverterRepository;

    @Autowired
    private EnergySampleRepository sampleRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @MockitoBean
    private RealtimePublisher realtimePublisher;

    /**
     * Timestamps estritamente crescentes entre TODOS os persists da classe:
     * os testes compartilham o banco e as regras olham a amostra MAIS
     * RECENTE — sem isso, a ordem de execução dos métodos mudaria qual
     * amostra é a última e os asserts ficariam não determinísticos.
     */
    private static final AtomicInteger SEQ = new AtomicInteger();

    private Long inverterId;

    @BeforeEach
    void setUp() {
        inverterId = inverterRepository.findBySerialNumber("CONFIGURAR-SN").orElseThrow().getId();
    }

    private Instant nextInstant() {
        return Instant.now().truncatedTo(ChronoUnit.MICROS).plusSeconds(SEQ.incrementAndGet());
    }

    @Test
    void offlineAlertFiresDeduplicatesAndAutoResolves() {
        touchInverter(Instant.now().minusSeconds(600)); // 10 min sem leitura

        alertEngine.evaluate(inverterId);
        alertEngine.evaluate(inverterId); // segunda passada não duplica

        var open = alertRepository.findAllByInverter_IdAndTypeAndStatusIn(
                inverterId, AlertType.INVERTER_OFFLINE, EnumSet.of(AlertStatus.ACTIVE));
        assertThat(open).hasSize(1);
        assertThat(open.get(0).getSeverity().name()).isEqualTo("CRITICAL");
        verify(realtimePublisher, atLeastOnce()).publishAlert(eq("CREATED"), any());

        touchInverter(Instant.now()); // voltou a responder
        alertEngine.evaluate(inverterId);

        assertThat(alertRepository.findAllByInverter_IdAndTypeAndStatusIn(
                inverterId, AlertType.INVERTER_OFFLINE, EnumSet.of(AlertStatus.ACTIVE))).isEmpty();
        verify(realtimePublisher, atLeastOnce()).publishAlert(eq("RESOLVED"), any());
    }

    @Test
    void lowBatteryFiresAndResolvesWithHysteresis() {
        touchInverter(Instant.now());
        persistSample(nextInstant(), 500, "10.00", InverterStatus.ONLINE);

        alertEngine.evaluate(inverterId);
        assertThat(openOf(AlertType.LOW_BATTERY)).hasSize(1);

        // SOC no limiar exato (15) NÃO resolve — histerese exige 15+3
        persistSample(nextInstant(), 500, "15.00", InverterStatus.ONLINE);
        alertEngine.evaluate(inverterId);
        assertThat(openOf(AlertType.LOW_BATTERY)).hasSize(1);

        persistSample(nextInstant(), 500, "40.00", InverterStatus.ONLINE);
        alertEngine.evaluate(inverterId);
        assertThat(openOf(AlertType.LOW_BATTERY)).isEmpty();
    }

    @Test
    void faultAlertFollowsInverterStatus() {
        touchInverter(Instant.now());
        persistSample(nextInstant(), 0, "80.00", InverterStatus.FAULT);

        alertEngine.evaluate(inverterId);
        assertThat(openOf(AlertType.INVERTER_FAULT)).hasSize(1);

        persistSample(nextInstant(), 3000, "80.00", InverterStatus.ONLINE);
        alertEngine.evaluate(inverterId);
        assertThat(openOf(AlertType.INVERTER_FAULT)).isEmpty();
    }

    private java.util.List<com.solarmonitor.alert.domain.Alert> openOf(AlertType type) {
        return alertRepository.findAllByInverter_IdAndTypeAndStatusIn(
                inverterId, type, EnumSet.of(AlertStatus.ACTIVE, AlertStatus.ACKNOWLEDGED));
    }

    private void touchInverter(Instant lastSeen) {
        transactionTemplate.executeWithoutResult(tx -> {
            Inverter managed = inverterRepository.findById(inverterId).orElseThrow();
            managed.setLastSeenAt(lastSeen);
        });
    }

    private void persistSample(Instant at, int acPowerW, String socPct, InverterStatus status) {
        transactionTemplate.executeWithoutResult(tx -> {
            Inverter managed = inverterRepository.findById(inverterId).orElseThrow();
            sampleRepository.save(EnergySample.builder()
                    .id(new EnergySampleId(null, at))
                    .inverter(managed)
                    .acPowerW(acPowerW)
                    .batterySocPct(new BigDecimal(socPct))
                    .inverterStatus(status)
                    .build());
        });
    }
}
