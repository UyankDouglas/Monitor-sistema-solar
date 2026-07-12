package com.solarmonitor.energy.service;

import com.solarmonitor.energy.domain.EnergySample;
import com.solarmonitor.energy.repository.EnergySampleRepository;
import com.solarmonitor.energy.web.dto.EnergySeriesDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Séries temporais para os gráficos. Resolução automática: períodos de até
 * {@value #RAW_WINDOW_HOURS} h retornam amostras brutas (5 s); acima disso o
 * downsampling é feito no banco via {@code time_bucket}, mirando
 * ~{@value #TARGET_POINTS} pontos por série — um dia inteiro vira ~500 pontos
 * em vez de 17.280.
 */
@Service
@RequiredArgsConstructor
public class HistoryService {

    static final int RAW_WINDOW_HOURS = 2;
    static final int TARGET_POINTS = 500;

    private final EnergySampleRepository energySampleRepository;

    @Transactional(readOnly = true)
    public EnergySeriesDto series(Long inverterId, Instant from, Instant to) {
        if (!from.isBefore(to)) {
            throw new IllegalArgumentException("Período inválido: 'from' deve ser anterior a 'to'");
        }
        Duration window = Duration.between(from, to);
        if (window.toHours() <= RAW_WINDOW_HOURS) {
            return rawSeries(inverterId, from, to);
        }
        return downsampledSeries(inverterId, from, to, bucketSecondsFor(window));
    }

    /** Janela de bucket para ~{@value #TARGET_POINTS} pontos, mínimo 30 s. */
    static int bucketSecondsFor(Duration window) {
        long ideal = window.toSeconds() / TARGET_POINTS;
        long rounded = Math.max(30, (ideal / 10) * 10);
        return (int) Math.min(rounded, 24 * 3600);
    }

    private EnergySeriesDto rawSeries(Long inverterId, Instant from, Instant to) {
        List<EnergySeriesDto.Point> points = energySampleRepository
                .findSeries(inverterId, from, to).stream()
                .map(this::toPoint)
                .toList();
        return new EnergySeriesDto(inverterId, from, to, 0, points);
    }

    private EnergySeriesDto downsampledSeries(Long inverterId, Instant from, Instant to, int bucketSeconds) {
        List<EnergySeriesDto.Point> points = energySampleRepository
                .findSeriesDownsampled(inverterId, from, to, bucketSeconds).stream()
                .map(row -> new EnergySeriesDto.Point(
                        toInstant(row[0]),
                        avg(row[1]),
                        avg(row[2]),
                        avg(row[3])))
                .toList();
        return new EnergySeriesDto(inverterId, from, to, bucketSeconds, points);
    }

    private EnergySeriesDto.Point toPoint(EnergySample sample) {
        return new EnergySeriesDto.Point(
                sample.getId().getSampledAt(),
                sample.getAcPowerW() == null ? null : BigDecimal.valueOf(sample.getAcPowerW()),
                sample.getLoadPowerW() == null ? null : BigDecimal.valueOf(sample.getLoadPowerW()),
                sample.getBatterySocPct());
    }

    private Instant toInstant(Object value) {
        if (value instanceof Instant instant) {
            return instant;
        }
        return ((Timestamp) value).toInstant();
    }

    private BigDecimal avg(Object value) {
        return value == null
                ? null
                : new BigDecimal(value.toString()).setScale(1, RoundingMode.HALF_UP);
    }
}
