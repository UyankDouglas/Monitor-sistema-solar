package com.solarmonitor.aggregation;

import com.solarmonitor.energy.domain.DailyGeneration;
import com.solarmonitor.energy.domain.EnergySample;
import com.solarmonitor.energy.domain.EnergySampleId;
import com.solarmonitor.energy.repository.DailyGenerationRepository;
import com.solarmonitor.energy.repository.EnergySampleRepository;
import com.solarmonitor.energy.repository.MonthlyGenerationRepository;
import com.solarmonitor.plant.domain.Inverter;
import com.solarmonitor.plant.domain.InverterStatus;
import com.solarmonitor.plant.repository.InverterRepository;
import com.solarmonitor.statistics.StatisticsDto;
import com.solarmonitor.statistics.StatisticsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Consolidação diária/mensal com amostras sintéticas de valores conhecidos —
 * cada número esperado é derivável à mão dos inputs (documentado inline).
 *
 * <p>Amostras são persistidas via {@link TransactionTemplate} com o inversor
 * recarregado NA MESMA transação: o {@code @MapsId} de {@code EnergySample}
 * exige associação gerenciada (lição das Etapas 4/5).</p>
 */
@Testcontainers
@SpringBootTest(properties = "app.scheduler.enabled=false")
class AggregationServiceIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("timescale/timescaledb:2.17.2-pg16")
                    .asCompatibleSubstituteFor("postgres"));

    private static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");
    private static final LocalDate DAY = LocalDate.of(2026, 7, 10);

    @Autowired
    private AggregationService aggregationService;

    @Autowired
    private StatisticsService statisticsService;

    @Autowired
    private EnergySampleRepository sampleRepository;

    @Autowired
    private DailyGenerationRepository dailyRepository;

    @Autowired
    private MonthlyGenerationRepository monthlyRepository;

    @Autowired
    private InverterRepository inverterRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    private Long inverterId;

    @BeforeEach
    void setUp() {
        inverterId = inverterRepository.findBySerialNumber("CONFIGURAR-SN").orElseThrow().getId();
    }

    @Test
    void consolidatesDayFromSyntheticSamplesAndRollsUpMonth() {
        // 4 amostras 30 s apart ao meio-dia local:
        //  ac:   5000, 6000, 5500, 4500 → pico 6000; mínimo gerando 4500
        //  load: 1200 constante → consumo = 1200 W × 90 s / 3.6e6 = 0.030 kWh
        //  export: 3000 constante → 0.075 kWh; import: 0
        //  contador diário: 10.0 → 10.3 (preferido sobre a integração)
        Instant noon = DAY.atTime(12, 0).atZone(ZONE).toInstant();
        persistSample(noon, 5000, "10.000");
        persistSample(noon.plusSeconds(30), 6000, "10.100");
        persistSample(noon.plusSeconds(60), 5500, "10.200");
        persistSample(noon.plusSeconds(90), 4500, "10.300");

        aggregationService.consolidateDay(inverterId, DAY, ZONE);
        aggregationService.consolidateMonth(inverterId, DAY);

        DailyGeneration daily = dailyRepository
                .findByInverter_IdAndGenerationDate(inverterId, DAY).orElseThrow();
        assertThat(daily.getEnergyKwh()).isEqualByComparingTo("10.300");     // contador do inversor
        assertThat(daily.getConsumptionKwh()).isEqualByComparingTo("0.030");
        assertThat(daily.getExportKwh()).isEqualByComparingTo("0.075");
        assertThat(daily.getImportKwh()).isEqualByComparingTo("0.000");
        assertThat(daily.getPeakPowerW()).isEqualTo(6000);
        assertThat(daily.getPeakAt()).isEqualTo(noon.plusSeconds(30));
        assertThat(daily.getMinPowerW()).isEqualTo(4500);
        // economia = 10.3 × 0.95 (tarifa seed) = 9.785 → 9.79
        assertThat(daily.getSavings()).isEqualByComparingTo("9.79");
        // CO₂ = 10.3 × 0.0817 = 0.84151 → 0.842
        assertThat(daily.getCo2AvoidedKg()).isEqualByComparingTo("0.842");
        // autoconsumo = 10.3 − 0.075 = 10.225; suficiência limitada a 100%
        assertThat(daily.getSelfConsumptionKwh()).isEqualByComparingTo("10.225");
        assertThat(daily.getSelfSufficiencyPct()).isEqualByComparingTo("100.00");

        var monthly = monthlyRepository.findByInverter_IdAndYearAndMonth(
                inverterId, (short) 2026, (short) 7).orElseThrow();
        assertThat(monthly.getEnergyKwh()).isEqualByComparingTo("10.300");

        // Idempotência: reconsolidar não duplica linhas
        aggregationService.consolidateDay(inverterId, DAY, ZONE);
        aggregationService.consolidateMonth(inverterId, DAY);
        assertThat(dailyRepository.findAllByInverter_IdAndGenerationDateBetweenOrderByGenerationDate(
                inverterId, DAY, DAY)).hasSize(1);
    }

    @Test
    void statisticsIdentifyBestAndWorstDays() {
        // MÊS distinto do outro teste (não só dias): o rollup mensal soma
        // todas as linhas do mês, e @SpringBootTest não faz rollback entre
        // métodos nem garante ordem de execução.
        LocalDate statsDay1 = DAY.plusMonths(1).withDayOfMonth(20);
        LocalDate statsDay2 = DAY.plusMonths(1).withDayOfMonth(21);
        persistSample(statsDay1.atTime(12, 0).atZone(ZONE).toInstant(), 5000, "10.300");
        persistSample(statsDay2.atTime(12, 0).atZone(ZONE).toInstant(), 8000, "20.000");
        aggregationService.consolidateDay(inverterId, statsDay1, ZONE);
        aggregationService.consolidateDay(inverterId, statsDay2, ZONE);

        StatisticsDto stats = statisticsService.compute(inverterId, statsDay1, statsDay2);

        assertThat(stats.daysWithData()).isEqualTo(2);
        assertThat(stats.bestDay().date()).isEqualTo(statsDay2);
        assertThat(stats.bestDay().energyKwh()).isEqualByComparingTo("20.000");
        assertThat(stats.worstDay().date()).isEqualTo(statsDay1);
        assertThat(stats.maxPeak().powerW()).isEqualTo(8000);
        assertThat(stats.minPeak().powerW()).isEqualTo(5000);
        assertThat(stats.totalEnergyKwh()).isEqualByComparingTo("30.300");
        assertThat(stats.avgDailyKwh()).isEqualByComparingTo("15.150");
        // Planta seed não tem capacidade instalada cadastrada
        assertThat(stats.kwhPerKwp()).isNull();
        assertThat(stats.capacityFactorPct()).isNull();
    }

    /** Persiste com inversor gerenciado na mesma transação (exigência do @MapsId). */
    private void persistSample(Instant at, int acPowerW, String dailyCounterKwh) {
        transactionTemplate.executeWithoutResult(tx -> {
            Inverter managed = inverterRepository.findById(inverterId).orElseThrow();
            sampleRepository.save(EnergySample.builder()
                    .id(new EnergySampleId(null, at))
                    .inverter(managed)
                    .acPowerW(acPowerW)
                    .loadPowerW(1200)
                    .exportPowerW(3000)
                    .importPowerW(0)
                    .dailyEnergyKwh(new BigDecimal(dailyCounterKwh))
                    .inverterStatus(InverterStatus.ONLINE)
                    .build());
        });
    }
}
