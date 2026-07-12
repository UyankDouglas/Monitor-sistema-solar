package com.solarmonitor.aggregation;

import com.solarmonitor.common.util.TimeZones;
import com.solarmonitor.energy.domain.DailyGeneration;
import com.solarmonitor.energy.domain.MonthlyGeneration;
import com.solarmonitor.energy.repository.DailyGenerationRepository;
import com.solarmonitor.energy.repository.EnergySampleRepository;
import com.solarmonitor.energy.repository.MonthlyGenerationRepository;
import com.solarmonitor.plant.domain.Inverter;
import com.solarmonitor.plant.domain.Plant;
import com.solarmonitor.plant.repository.InverterRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/**
 * Consolida as amostras brutas em {@code daily_generation} e
 * {@code monthly_generation} — a fonte dos gráficos de barras, economia e
 * estatísticas. Upserts idempotentes: reconsolidar o mesmo dia apenas
 * atualiza a linha existente.
 *
 * <p>Geração do dia: preferimos o <em>contador diário do inversor</em>
 * (máximo de {@code daily_energy_kwh} no dia — medição do próprio
 * equipamento, imune a buracos de coleta); fallback para a integração
 * tempo-ponderada de {@code ac_power_w} quando a origem não fornece o
 * contador. Consumo/exportação/importação são sempre integrados (não há
 * contadores correspondentes confiáveis em todas as origens).</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AggregationService {

    private final EnergySampleRepository energySampleRepository;
    private final DailyGenerationRepository dailyRepository;
    private final MonthlyGenerationRepository monthlyRepository;
    private final InverterRepository inverterRepository;

    /**
     * Consolida hoje + ontem e o mês corrente + anterior de um inversor.
     * Janela dupla proposital: reconsolidar ontem logo após a virada captura
     * as últimas amostras do dia; idem para o mês.
     *
     * <p>DEVE ser chamado através do proxy Spring (o loop sobre inversores
     * fica no {@code AggregationScheduler}) — a transação garante a sessão
     * aberta para o acesso lazy a {@code inverter.plant}.</p>
     */
    @Transactional
    public void consolidateInverter(Long inverterId) {
        Inverter inverter = managed(inverterId);
        ZoneId zone = TimeZones.of(inverter.getPlant());
        LocalDate today = LocalDate.now(zone);

        doConsolidateDay(inverter, today, zone);
        doConsolidateDay(inverter, today.minusDays(1), zone);
        doConsolidateMonth(inverter, today);
        doConsolidateMonth(inverter, today.minusMonths(1));
    }

    /** Consolida um dia específico; sem amostras no dia, não cria linha. */
    @Transactional
    public void consolidateDay(Long inverterId, LocalDate date, ZoneId zone) {
        doConsolidateDay(managed(inverterId), date, zone);
    }

    /** Rollup mensal a partir das linhas diárias; sem dias, não cria linha. */
    @Transactional
    public void consolidateMonth(Long inverterId, LocalDate anyDayInMonth) {
        doConsolidateMonth(managed(inverterId), anyDayInMonth);
    }

    /**
     * Sempre recarrega o inversor na transação corrente: entidades vindas de
     * fora estão detached e quebrariam o {@code @MapsId}/lazy loading — mesma
     * lição da camada de ingestão.
     */
    private Inverter managed(Long inverterId) {
        return inverterRepository.findById(inverterId).orElseThrow();
    }

    private void doConsolidateDay(Inverter inverter, LocalDate date, ZoneId zone) {
        Instant from = date.atStartOfDay(zone).toInstant();
        Instant to = date.plusDays(1).atStartOfDay(zone).toInstant();

        List<Object[]> rows = energySampleRepository.daySummary(inverter.getId(), from, to);
        Object[] row = rows.isEmpty() ? null : rows.get(0);
        long sampleCount = row == null ? 0 : ((Number) row[8]).longValue();
        if (sampleCount == 0) {
            return;
        }

        BigDecimal consumption = decimal(row[0]);
        BigDecimal exported = decimal(row[1]);
        BigDecimal imported = decimal(row[2]);
        BigDecimal integrated = decimal(row[3]);
        Integer peakPowerW = row[4] == null ? null : ((Number) row[4]).intValue();
        Instant peakAt = toInstant(row[5]);
        Integer minGenPowerW = row[6] == null ? null : ((Number) row[6]).intValue();
        BigDecimal dailyCounter = row[7] == null ? null : decimal(row[7]);

        BigDecimal energy = chooseEnergy(inverter.getId(), date, dailyCounter, integrated);

        Plant plant = inverter.getPlant();
        BigDecimal savings = energy.multiply(plant.getKwhPrice()).setScale(2, RoundingMode.HALF_UP);
        BigDecimal co2 = energy.multiply(plant.getCo2FactorKgPerKwh()).setScale(3, RoundingMode.HALF_UP);
        BigDecimal selfConsumption = energy.subtract(exported).max(BigDecimal.ZERO);
        BigDecimal selfSufficiency = consumption.signum() > 0
                ? selfConsumption.min(consumption)
                        .multiply(BigDecimal.valueOf(100))
                        .divide(consumption, 2, RoundingMode.HALF_UP)
                : null;

        DailyGeneration daily = dailyRepository
                .findByInverter_IdAndGenerationDate(inverter.getId(), date)
                .orElseGet(() -> DailyGeneration.builder()
                        .inverter(inverter)
                        .generationDate(date)
                        .build());
        daily.setEnergyKwh(energy.setScale(3, RoundingMode.HALF_UP));
        daily.setPeakPowerW(peakPowerW);
        daily.setPeakAt(peakAt);
        daily.setMinPowerW(minGenPowerW);
        daily.setConsumptionKwh(consumption);
        daily.setExportKwh(exported);
        daily.setImportKwh(imported);
        daily.setSelfConsumptionKwh(selfConsumption.setScale(3, RoundingMode.HALF_UP));
        daily.setSelfSufficiencyPct(selfSufficiency);
        daily.setSavings(savings);
        daily.setCo2AvoidedKg(co2);
        dailyRepository.save(daily);
    }

    private void doConsolidateMonth(Inverter inverter, LocalDate anyDayInMonth) {
        LocalDate first = anyDayInMonth.withDayOfMonth(1);
        LocalDate last = first.plusMonths(1).minusDays(1);
        List<DailyGeneration> days = dailyRepository
                .findAllByInverter_IdAndGenerationDateBetweenOrderByGenerationDate(
                        inverter.getId(), first, last);
        if (days.isEmpty()) {
            return;
        }

        MonthlyGeneration monthly = monthlyRepository
                .findByInverter_IdAndYearAndMonth(inverter.getId(),
                        (short) first.getYear(), (short) first.getMonthValue())
                .orElseGet(() -> MonthlyGeneration.builder()
                        .inverter(inverter)
                        .year((short) first.getYear())
                        .month((short) first.getMonthValue())
                        .build());
        monthly.setEnergyKwh(sum(days, DailyGeneration::getEnergyKwh));
        monthly.setConsumptionKwh(sum(days, DailyGeneration::getConsumptionKwh));
        monthly.setExportKwh(sum(days, DailyGeneration::getExportKwh));
        monthly.setImportKwh(sum(days, DailyGeneration::getImportKwh));
        monthly.setSavings(sum(days, DailyGeneration::getSavings));
        monthly.setCo2AvoidedKg(sum(days, DailyGeneration::getCo2AvoidedKg));
        monthlyRepository.save(monthly);
    }

    private BigDecimal sum(List<DailyGeneration> days,
                           java.util.function.Function<DailyGeneration, BigDecimal> field) {
        return days.stream()
                .map(field)
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Prefere o contador do inversor (medição do equipamento, imune a buracos
     * de coleta); cai para a integração quando o contador está ausente, zerado
     * ou implausível (mais que o dobro de uma integração com volume relevante
     * — sintoma de contador stale ou RTC desajustado).
     */
    private BigDecimal chooseEnergy(Long inverterId, LocalDate date,
                                    BigDecimal counter, BigDecimal integrated) {
        if (counter == null || counter.signum() <= 0) {
            return integrated;
        }
        boolean integrationMeaningful = integrated.compareTo(BigDecimal.ONE) >= 0;
        if (integrationMeaningful && counter.compareTo(integrated.multiply(BigDecimal.TWO)) > 0) {
            log.warn("Inversor {} dia {}: contador diário ({} kWh) implausível vs integração ({} kWh); "
                    + "usando a integração", inverterId, date, counter, integrated);
            return integrated;
        }
        return counter;
    }

    private BigDecimal decimal(Object value) {
        return value == null
                ? BigDecimal.ZERO.setScale(3)
                : new BigDecimal(value.toString()).setScale(3, RoundingMode.HALF_UP);
    }

    /** Hibernate 6 devolve timestamptz de query nativa como Instant; JDBC puro, como Timestamp. */
    private Instant toInstant(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Instant instant) {
            return instant;
        }
        return ((Timestamp) value).toInstant();
    }
}
