package com.solarmonitor.energy.web.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Série temporal para gráficos. {@code bucketSeconds} informa a resolução:
 * 0 = amostras brutas; &gt;0 = médias por janela (downsampling no banco).
 */
public record EnergySeriesDto(
        Long inverterId,
        Instant from,
        Instant to,
        int bucketSeconds,
        List<Point> points) {

    public record Point(
            Instant timestamp,
            BigDecimal acPowerW,
            BigDecimal loadPowerW,
            BigDecimal batterySocPct) {
    }
}
