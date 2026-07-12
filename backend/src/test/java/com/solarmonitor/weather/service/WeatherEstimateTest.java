package com.solarmonitor.weather.service;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class WeatherEstimateTest {

    @Test
    void expectedGenerationFollowsRadiationCapacityAndPerformanceRatio() {
        // 18 MJ/m² = 5 kWh/m²; × 10 kWp × 0.8 = 40 kWh
        assertThat(WeatherService.expectedKwh(new BigDecimal("18"), new BigDecimal("10")))
                .isEqualByComparingTo("40.000");
        // Dia nublado: 3.6 MJ = 1 kWh/m² × 10 × 0.8 = 8 kWh
        assertThat(WeatherService.expectedKwh(new BigDecimal("3.6"), new BigDecimal("10")))
                .isEqualByComparingTo("8.000");
    }

    @Test
    void missingInputsYieldNullNotZero() {
        assertThat(WeatherService.expectedKwh(null, new BigDecimal("10"))).isNull();
        assertThat(WeatherService.expectedKwh(new BigDecimal("18"), null)).isNull();
        assertThat(WeatherService.expectedKwh(new BigDecimal("18"), BigDecimal.ZERO)).isNull();
    }

    @Test
    void wmoCodesTranslateToPortuguese() {
        assertThat(OpenMeteoClient.describe(0)).isEqualTo("Céu limpo");
        assertThat(OpenMeteoClient.describe(3)).isEqualTo("Nublado");
        assertThat(OpenMeteoClient.describe(61)).isEqualTo("Chuva");
        assertThat(OpenMeteoClient.describe(95)).isEqualTo("Tempestade");
        assertThat(OpenMeteoClient.describe(null)).isEqualTo("—");
        assertThat(OpenMeteoClient.describe(42)).isEqualTo("Código 42");
    }
}
