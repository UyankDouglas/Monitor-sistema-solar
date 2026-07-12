package com.solarmonitor.dashboard;

import com.solarmonitor.alert.domain.AlertStatus;
import com.solarmonitor.common.util.TimeZones;
import com.solarmonitor.alert.repository.AlertRepository;
import com.solarmonitor.energy.domain.EnergySample;
import com.solarmonitor.energy.repository.DailyGenerationRepository;
import com.solarmonitor.energy.repository.EnergySampleRepository;
import com.solarmonitor.energy.repository.MonthlyGenerationRepository;
import com.solarmonitor.plant.domain.Inverter;
import com.solarmonitor.plant.domain.Plant;
import com.solarmonitor.plant.repository.InverterRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Monta o payload do dashboard: última amostra (instantâneos) + agregações
 * (hoje/mês) + estimativas totais derivadas do contador acumulado do inversor
 * com a tarifa/fator atuais da planta.
 *
 * <p>"Hoje" prioriza o contador da última amostra (tempo real); a linha
 * consolidada de {@code daily_generation} cobre o caso de o dia ter virado
 * sem novas amostras ainda.</p>
 */
@Service
@RequiredArgsConstructor
public class DashboardService {

    private final EnergySampleRepository energySampleRepository;
    private final DailyGenerationRepository dailyRepository;
    private final MonthlyGenerationRepository monthlyRepository;
    private final AlertRepository alertRepository;
    private final InverterRepository inverterRepository;

    /**
     * Recebe o id (não a entidade): o inversor é recarregado gerenciado nesta
     * transação — associação lazy {@code plant} acessível com segurança.
     */
    @Transactional(readOnly = true)
    public DashboardDto build(Long inverterId) {
        Inverter inverter = inverterRepository.findById(inverterId).orElseThrow(
                () -> new EntityNotFoundException("Inversor " + inverterId + " não encontrado"));
        Plant plant = inverter.getPlant();
        ZoneId zone = TimeZones.of(plant);
        LocalDate today = LocalDate.now(zone);

        EnergySample latest = energySampleRepository
                .findFirstById_InverterIdOrderById_SampledAtDesc(inverter.getId())
                .orElse(null);

        // Amostra só conta como "hoje" se for do dia corrente no fuso da planta.
        boolean latestIsToday = latest != null
                && LocalDate.ofInstant(latest.getId().getSampledAt(), zone).equals(today);

        var todayRow = dailyRepository.findByInverter_IdAndGenerationDate(inverter.getId(), today);
        BigDecimal todayEnergy = latestIsToday && latest.getDailyEnergyKwh() != null
                ? latest.getDailyEnergyKwh()
                : todayRow.map(d -> d.getEnergyKwh()).orElse(BigDecimal.ZERO);

        var monthRow = monthlyRepository.findByInverter_IdAndYearAndMonth(
                inverter.getId(), (short) today.getYear(), (short) today.getMonthValue());
        BigDecimal monthEnergy = monthRow.map(m -> m.getEnergyKwh()).orElse(todayEnergy);

        BigDecimal totalEnergy = latest == null ? null : latest.getTotalEnergyKwh();

        BigDecimal todaySavings = todayRow.map(d -> d.getSavings())
                .orElseGet(() -> money(todayEnergy, plant.getKwhPrice()));
        BigDecimal monthSavings = monthRow.map(m -> m.getSavings())
                .orElse(todaySavings);
        BigDecimal totalSavings = totalEnergy == null ? null : money(totalEnergy, plant.getKwhPrice());
        BigDecimal totalCo2 = totalEnergy == null ? null
                : totalEnergy.multiply(plant.getCo2FactorKgPerKwh()).setScale(1, RoundingMode.HALF_UP);

        return new DashboardDto(
                inverter.getId(),
                inverter.getName(),
                inverter.getStatus().name(),
                inverter.getLastSeenAt(),
                latest == null ? null : latest.getId().getSampledAt(),
                latest == null ? null : latest.getAcPowerW(),
                latest == null ? null : latest.getLoadPowerW(),
                latest == null ? null : latest.getExportPowerW(),
                latest == null ? null : latest.getImportPowerW(),
                latest == null ? null : latest.getBatteryPowerW(),
                latest == null ? null : latest.getBatterySocPct(),
                latest == null ? null : latest.getBatteryVoltage(),
                latest == null ? null : latest.getBatteryTemperatureC(),
                latest == null ? null : latest.getGridVoltageL1(),
                latest == null ? null : latest.getGridVoltageL2(),
                latest == null ? null : latest.getGridVoltageL3(),
                latest == null ? null : latest.getGridFrequencyHz(),
                latest == null ? null : latest.getInverterTemperatureC(),
                todayEnergy,
                monthEnergy,
                totalEnergy,
                plant.getCurrency(),
                todaySavings,
                monthSavings,
                totalSavings,
                totalCo2,
                alertRepository.countByStatus(AlertStatus.ACTIVE));
    }

    private BigDecimal money(BigDecimal energyKwh, BigDecimal price) {
        return energyKwh.multiply(price).setScale(2, RoundingMode.HALF_UP);
    }
}
