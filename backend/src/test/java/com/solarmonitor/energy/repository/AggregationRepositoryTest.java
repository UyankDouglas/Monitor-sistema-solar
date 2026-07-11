package com.solarmonitor.energy.repository;

import com.solarmonitor.AbstractRepositoryTest;
import com.solarmonitor.energy.domain.DailyGeneration;
import com.solarmonitor.energy.domain.MonthlyGeneration;
import com.solarmonitor.plant.domain.Inverter;
import com.solarmonitor.plant.repository.InverterRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AggregationRepositoryTest extends AbstractRepositoryTest {

    @Autowired
    private DailyGenerationRepository dailyRepository;

    @Autowired
    private MonthlyGenerationRepository monthlyRepository;

    @Autowired
    private InverterRepository inverterRepository;

    private Inverter inverter;

    @BeforeEach
    void setUp() {
        inverter = inverterRepository.findBySerialNumber("CONFIGURAR-SN").orElseThrow();
    }

    @Test
    void dailyRangeQueryAndStatistics() {
        persistDaily(LocalDate.of(2026, 7, 1), "42.500");
        persistDaily(LocalDate.of(2026, 7, 2), "55.100"); // melhor dia
        persistDaily(LocalDate.of(2026, 7, 3), "8.300");  // pior dia

        List<DailyGeneration> range = dailyRepository
                .findAllByInverter_IdAndGenerationDateBetweenOrderByGenerationDate(
                        inverter.getId(), LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31));
        assertThat(range).hasSize(3);

        assertThat(dailyRepository.findFirstByInverter_IdOrderByEnergyKwhDesc(inverter.getId()))
                .hasValueSatisfying(best -> assertThat(best.getEnergyKwh()).isEqualByComparingTo("55.100"));

        assertThat(dailyRepository.findFirstByInverter_IdAndEnergyKwhGreaterThanOrderByEnergyKwhAsc(
                inverter.getId(), BigDecimal.ZERO))
                .hasValueSatisfying(worst -> assertThat(worst.getEnergyKwh()).isEqualByComparingTo("8.300"));
    }

    @Test
    void duplicateDailyRowForSameDayIsRejected() {
        persistDaily(LocalDate.of(2026, 6, 15), "30.000");

        assertThatThrownBy(() -> persistDaily(LocalDate.of(2026, 6, 15), "31.000"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void monthlyUniqueKeyLookup() {
        MonthlyGeneration june = MonthlyGeneration.builder()
                .inverter(inverter)
                .year((short) 2026)
                .month((short) 6)
                .energyKwh(new BigDecimal("1240.750"))
                .savings(new BigDecimal("1178.71"))
                .build();
        monthlyRepository.saveAndFlush(june);

        assertThat(monthlyRepository.findByInverter_IdAndYearAndMonth(inverter.getId(), (short) 2026, (short) 6))
                .hasValueSatisfying(m -> assertThat(m.getEnergyKwh()).isEqualByComparingTo("1240.750"));
        assertThat(monthlyRepository.findAllByInverter_IdAndYearOrderByMonth(inverter.getId(), (short) 2026))
                .hasSize(1);
    }

    private void persistDaily(LocalDate date, String energyKwh) {
        dailyRepository.saveAndFlush(DailyGeneration.builder()
                .inverter(inverter)
                .generationDate(date)
                .energyKwh(new BigDecimal(energyKwh))
                .peakPowerW(8200)
                .build());
    }
}
