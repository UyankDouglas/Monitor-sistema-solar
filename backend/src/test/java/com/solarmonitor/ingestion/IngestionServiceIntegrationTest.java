package com.solarmonitor.ingestion;

import com.solarmonitor.energy.domain.EnergySample;
import com.solarmonitor.energy.repository.EnergySampleRepository;
import com.solarmonitor.energy.repository.MpptReadingRepository;
import com.solarmonitor.plant.domain.Inverter;
import com.solarmonitor.plant.domain.InverterStatus;
import com.solarmonitor.plant.repository.InverterRepository;
import com.solarmonitor.realtime.RealtimePublisher;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Ponta a ponta da ingestão com o provider SIMULATED (padrão de fábrica via
 * V9): ciclo completo → amostra + MPPTs persistidos, inversor atualizado e
 * broadcast disparado. Container próprio: os commits reais desta suíte não
 * podem vazar para os testes de repositório (que compartilham outro banco).
 */
@Testcontainers
@SpringBootTest(properties = "app.scheduler.enabled=false")
class IngestionServiceIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("timescale/timescaledb:2.17.2-pg16")
                    .asCompatibleSubstituteFor("postgres"));

    @Autowired
    private IngestionService ingestionService;

    @Autowired
    private InverterRepository inverterRepository;

    @Autowired
    private EnergySampleRepository energySampleRepository;

    @Autowired
    private MpptReadingRepository mpptReadingRepository;

    @MockitoBean
    private RealtimePublisher realtimePublisher;

    @Test
    void fullCyclePersistsAndBroadcasts() {
        Inverter inverter = inverterRepository.findBySerialNumber("CONFIGURAR-SN").orElseThrow();
        assertThat(energySampleRepository.count()).isZero();

        ingestionService.ingestAll();
        ingestionService.ingestAll();

        assertThat(energySampleRepository.count()).isEqualTo(2);
        assertThat(mpptReadingRepository.count()).isEqualTo(4); // 2 strings × 2 ciclos

        EnergySample latest = energySampleRepository
                .findFirstById_InverterIdOrderById_SampledAtDesc(inverter.getId())
                .orElseThrow();
        assertThat(latest.getAcPowerW()).isNotNull();
        assertThat(latest.getInverterStatus()).isEqualTo(InverterStatus.ONLINE);
        assertThat(latest.getBatterySocPct()).isNotNull();

        Inverter reloaded = inverterRepository.findById(inverter.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(InverterStatus.ONLINE);
        assertThat(reloaded.getLastSeenAt()).isEqualTo(latest.getId().getSampledAt());

        verify(realtimePublisher, times(2)).publishReading(eq(inverter.getId()), any());
    }
}
