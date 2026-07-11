package com.solarmonitor.energy.repository;

import com.solarmonitor.AbstractRepositoryTest;
import com.solarmonitor.energy.domain.EnergySample;
import com.solarmonitor.energy.domain.EnergySampleId;
import com.solarmonitor.energy.domain.MpptReading;
import com.solarmonitor.energy.domain.MpptReadingId;
import com.solarmonitor.plant.domain.Inverter;
import com.solarmonitor.plant.domain.InverterStatus;
import com.solarmonitor.plant.repository.InverterRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TelemetryRepositoryTest extends AbstractRepositoryTest {

    @Autowired
    private EnergySampleRepository energySampleRepository;

    @Autowired
    private MpptReadingRepository mpptReadingRepository;

    @Autowired
    private InverterRepository inverterRepository;

    @Autowired
    private EntityManager entityManager;

    private Inverter inverter;

    /** Base de tempo fixa e truncada a micros (precisão do timestamptz). */
    private final Instant t0 = Instant.parse("2026-07-10T12:00:00Z").truncatedTo(ChronoUnit.MICROS);

    @BeforeEach
    void setUp() {
        inverter = inverterRepository.findBySerialNumber("CONFIGURAR-SN").orElseThrow();
    }

    @Test
    void energySampleAndMpptTablesAreHypertables() {
        Number count = (Number) entityManager.createNativeQuery("""
                        select count(*) from timescaledb_information.hypertables
                        where hypertable_name in ('energy_sample', 'mppt_reading')
                        """)
                .getSingleResult();
        assertThat(count.longValue()).isEqualTo(2);
    }

    @Test
    void persistsAndReadsFullSampleWithMpptReadings() {
        persistSample(t0, 5200, new BigDecimal("12.345"));
        persistMppt(t0, (short) 1, 2600);
        persistMppt(t0, (short) 2, 2650);
        entityManager.flush();
        entityManager.clear();

        EnergySample reloaded = energySampleRepository
                .findFirstById_InverterIdOrderById_SampledAtDesc(inverter.getId())
                .orElseThrow();

        assertThat(reloaded.getId().getSampledAt()).isEqualTo(t0);
        assertThat(reloaded.getAcPowerW()).isEqualTo(5200);
        assertThat(reloaded.getDailyEnergyKwh()).isEqualByComparingTo("12.345");
        assertThat(reloaded.getBatterySocPct()).isEqualByComparingTo("78.50");
        assertThat(reloaded.getInverterStatus()).isEqualTo(InverterStatus.ONLINE);

        List<MpptReading> strings = mpptReadingRepository
                .findAllById_InverterIdAndId_SampledAtOrderById_StringIndex(inverter.getId(), t0);
        assertThat(strings).hasSize(2);
        assertThat(strings.get(0).getId().getStringIndex()).isEqualTo((short) 1);
        assertThat(strings.get(1).getPowerW()).isEqualTo(2650);
    }

    @Test
    void repositorySaveUsesPersistAndFillsDerivedId() {
        Instant at = t0.plusSeconds(3600);
        EnergySample sample = EnergySample.builder()
                .id(new EnergySampleId(null, at))
                .inverter(inverter)
                .acPowerW(1234)
                .build();
        energySampleRepository.save(sample);

        MpptReading reading = MpptReading.builder()
                .id(new MpptReadingId(null, at, (short) 1))
                .inverter(inverter)
                .powerW(600)
                .build();
        mpptReadingRepository.saveAll(List.of(reading));
        entityManager.flush();

        // Persistable.isNew()=true => persist direto: o @MapsId preenche o id
        // da PRÓPRIA instância passada ao save (merge devolveria uma cópia).
        assertThat(sample.getId().getInverterId()).isEqualTo(inverter.getId());
        assertThat(reading.getId().getInverterId()).isEqualTo(inverter.getId());
    }

    @Test
    void findSeriesReturnsOrderedRangeExclusiveAtEnd() {
        for (int i = 0; i < 5; i++) {
            persistSample(t0.plusSeconds(5L * i), 4000 + i, null);
        }
        entityManager.flush();
        entityManager.clear();

        List<EnergySample> series = energySampleRepository.findSeries(
                inverter.getId(), t0, t0.plusSeconds(15)); // [t0, t0+15s)

        assertThat(series).hasSize(3);
        assertThat(series.get(0).getAcPowerW()).isEqualTo(4000);
        assertThat(series.get(2).getAcPowerW()).isEqualTo(4002);
    }

    @Test
    void downsampledSeriesAggregatesByTimeBucket() {
        for (int i = 0; i < 12; i++) {
            persistSample(t0.plusSeconds(5L * i), 6000, null); // 60 s de amostras a cada 5 s
        }
        entityManager.flush();

        List<Object[]> buckets = energySampleRepository.findSeriesDownsampled(
                inverter.getId(), t0, t0.plusSeconds(60), 30);

        assertThat(buckets).hasSize(2); // 60 s / buckets de 30 s
        assertThat(((Number) buckets.get(0)[1]).doubleValue()).isEqualTo(6000.0);
    }

    private void persistSample(Instant at, int acPowerW, BigDecimal dailyKwh) {
        EnergySample sample = EnergySample.builder()
                .id(new EnergySampleId(null, at))
                .inverter(inverter)
                .acPowerW(acPowerW)
                .loadPowerW(1800)
                .exportPowerW(3000)
                .importPowerW(0)
                .batteryPowerW(-400)
                .dailyEnergyKwh(dailyKwh)
                .totalEnergyKwh(new BigDecimal("15230.500"))
                .gridVoltageL1(new BigDecimal("220.1"))
                .gridVoltageL2(new BigDecimal("219.8"))
                .gridVoltageL3(new BigDecimal("220.4"))
                .gridFrequencyHz(new BigDecimal("60.02"))
                .batteryVoltage(new BigDecimal("52.3"))
                .batterySocPct(new BigDecimal("78.50"))
                .batteryTemperatureC(new BigDecimal("31.0"))
                .inverterTemperatureC(new BigDecimal("47.5"))
                .inverterStatus(InverterStatus.ONLINE)
                .build();
        entityManager.persist(sample);
    }

    private void persistMppt(Instant at, short stringIndex, int powerW) {
        MpptReading reading = MpptReading.builder()
                .id(new MpptReadingId(null, at, stringIndex))
                .inverter(inverter)
                .voltage(new BigDecimal("380.5"))
                .currentA(new BigDecimal("6.85"))
                .powerW(powerW)
                .build();
        entityManager.persist(reading);
    }
}
