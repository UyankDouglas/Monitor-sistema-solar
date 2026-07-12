package com.solarmonitor.statistics;

import com.solarmonitor.energy.domain.DailyGeneration;
import com.solarmonitor.energy.repository.DailyGenerationRepository;
import com.solarmonitor.plant.domain.Inverter;
import com.solarmonitor.plant.repository.InverterRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Estatísticas calculadas sobre {@code daily_generation}. O volume é pequeno
 * (1 linha/dia/inversor — décadas cabem em memória), então o cálculo em
 * stream é mais claro e testável que SQL agregado.
 */
@Service
@RequiredArgsConstructor
public class StatisticsService {

    private final DailyGenerationRepository dailyRepository;
    private final InverterRepository inverterRepository;

    /** Recebe id e recarrega gerenciado — o acesso lazy a plant exige transação própria. */
    @Transactional(readOnly = true)
    public StatisticsDto compute(Long inverterId, LocalDate from, LocalDate to) {
        if (from.isAfter(to)) {
            throw new IllegalArgumentException("Período inválido: 'from' deve ser anterior ou igual a 'to'");
        }
        Inverter inverter = inverterRepository.findById(inverterId).orElseThrow(
                () -> new EntityNotFoundException("Inversor " + inverterId + " não encontrado"));
        List<DailyGeneration> days = dailyRepository
                .findAllByInverter_IdAndGenerationDateBetweenOrderByGenerationDate(
                        inverter.getId(), from, to);
        List<DailyGeneration> generatingDays = days.stream()
                .filter(d -> d.getEnergyKwh() != null && d.getEnergyKwh().signum() > 0)
                .toList();

        BigDecimal totalEnergy = sum(days, DailyGeneration::getEnergyKwh);
        BigDecimal totalSavings = sum(days, DailyGeneration::getSavings);
        BigDecimal totalCo2 = sum(days, DailyGeneration::getCo2AvoidedKg);

        var bestDay = generatingDays.stream()
                .max(Comparator.comparing(DailyGeneration::getEnergyKwh))
                .map(d -> new StatisticsDto.DayStat(d.getGenerationDate(), d.getEnergyKwh()))
                .orElse(null);
        var worstDay = generatingDays.stream()
                .min(Comparator.comparing(DailyGeneration::getEnergyKwh))
                .map(d -> new StatisticsDto.DayStat(d.getGenerationDate(), d.getEnergyKwh()))
                .orElse(null);

        var maxPeak = days.stream()
                .filter(d -> d.getPeakPowerW() != null)
                .max(Comparator.comparing(DailyGeneration::getPeakPowerW))
                .map(d -> new StatisticsDto.PeakStat(d.getPeakPowerW(), d.getGenerationDate(), d.getPeakAt()))
                .orElse(null);
        var minPeak = days.stream()
                .filter(d -> d.getPeakPowerW() != null)
                .min(Comparator.comparing(DailyGeneration::getPeakPowerW))
                .map(d -> new StatisticsDto.PeakStat(d.getPeakPowerW(), d.getGenerationDate(), d.getPeakAt()))
                .orElse(null);

        BigDecimal avgDaily = generatingDays.isEmpty() ? null
                : totalEnergy.divide(BigDecimal.valueOf(generatingDays.size()), 3, RoundingMode.HALF_UP);
        // Média mensal aproximada: média diária × 30,44 (dias médios/mês).
        BigDecimal avgMonthly = avgDaily == null ? null
                : avgDaily.multiply(new BigDecimal("30.44")).setScale(1, RoundingMode.HALF_UP);

        BigDecimal capacityKwp = inverter.getPlant().getInstalledCapacityKwp();
        BigDecimal kwhPerKwp = null;
        BigDecimal capacityFactor = null;
        if (capacityKwp != null && capacityKwp.signum() > 0 && !days.isEmpty()) {
            kwhPerKwp = totalEnergy.divide(capacityKwp, 1, RoundingMode.HALF_UP);
            BigDecimal theoreticalMax = capacityKwp
                    .multiply(BigDecimal.valueOf(days.size() * 24L)); // kWh se gerasse 24 h a plena potência
            capacityFactor = totalEnergy.multiply(BigDecimal.valueOf(100))
                    .divide(theoreticalMax, 2, RoundingMode.HALF_UP);
        }

        return new StatisticsDto(from, to, days.size(), totalEnergy, totalSavings, totalCo2,
                bestDay, worstDay, maxPeak, minPeak, avgDaily, avgMonthly, kwhPerKwp, capacityFactor);
    }

    private BigDecimal sum(List<DailyGeneration> days,
                           java.util.function.Function<DailyGeneration, BigDecimal> field) {
        return days.stream().map(field).filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
