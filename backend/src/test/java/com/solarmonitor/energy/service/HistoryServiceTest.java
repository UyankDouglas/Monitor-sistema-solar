package com.solarmonitor.energy.service;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class HistoryServiceTest {

    @Test
    void bucketSizingTargetsAboutFiveHundredPoints() {
        // 24 h / 500 = 172,8 s → arredonda p/ baixo em passos de 10 → 170 s
        assertThat(HistoryService.bucketSecondsFor(Duration.ofHours(24))).isEqualTo(170);
        // 7 dias → 1209,6 → 1200 s (20 min)
        assertThat(HistoryService.bucketSecondsFor(Duration.ofDays(7))).isEqualTo(1200);
        // Janela pequena nunca desce do piso de 30 s
        assertThat(HistoryService.bucketSecondsFor(Duration.ofHours(3))).isEqualTo(30);
        // 1 ano → 31.536.000/500 = 63.072 → arredonda p/ 63.070 s (~17,5 h)
        assertThat(HistoryService.bucketSecondsFor(Duration.ofDays(365))).isEqualTo(63070);
    }
}
