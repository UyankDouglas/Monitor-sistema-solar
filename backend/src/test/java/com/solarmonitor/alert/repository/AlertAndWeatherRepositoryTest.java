package com.solarmonitor.alert.repository;

import com.solarmonitor.AbstractRepositoryTest;
import com.solarmonitor.alert.domain.Alert;
import com.solarmonitor.alert.domain.AlertSeverity;
import com.solarmonitor.alert.domain.AlertStatus;
import com.solarmonitor.alert.domain.AlertType;
import com.solarmonitor.plant.domain.Inverter;
import com.solarmonitor.plant.repository.InverterRepository;
import com.solarmonitor.plant.repository.PlantRepository;
import com.solarmonitor.weather.domain.Weather;
import com.solarmonitor.weather.repository.WeatherRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AlertAndWeatherRepositoryTest extends AbstractRepositoryTest {

    @Autowired
    private AlertRepository alertRepository;

    @Autowired
    private AlertRuleRepository alertRuleRepository;

    @Autowired
    private InverterRepository inverterRepository;

    @Autowired
    private PlantRepository plantRepository;

    @Autowired
    private WeatherRepository weatherRepository;

    @Test
    void defaultAlertRulesSeededForAllTypes() {
        assertThat(alertRuleRepository.findAll())
                .extracting(rule -> rule.getType())
                .containsExactlyInAnyOrder(AlertType.values());

        assertThat(alertRuleRepository.findByType(AlertType.LOW_BATTERY))
                .hasValueSatisfying(rule -> {
                    assertThat(rule.isEnabled()).isTrue();
                    assertThat(rule.getThreshold()).containsEntry("min_soc_pct", 15);
                });
    }

    @Test
    void alertLifecycleAndDeduplicationQuery() {
        Inverter inverter = inverterRepository.findBySerialNumber("CONFIGURAR-SN").orElseThrow();
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);

        Alert offline = Alert.builder()
                .inverter(inverter)
                .type(AlertType.INVERTER_OFFLINE)
                .severity(AlertSeverity.CRITICAL)
                .message("Sem leituras há mais de 120 s")
                .details(Map.of("offline_after_seconds", 120))
                .triggeredAt(now)
                .build();
        alertRepository.saveAndFlush(offline);

        // Consulta de deduplicação: existe alerta ativo desse tipo?
        assertThat(alertRepository.findFirstByInverter_IdAndTypeAndStatusOrderByTriggeredAtDesc(
                inverter.getId(), AlertType.INVERTER_OFFLINE, AlertStatus.ACTIVE)).isPresent();
        assertThat(alertRepository.countByStatus(AlertStatus.ACTIVE)).isEqualTo(1);

        offline.setStatus(AlertStatus.RESOLVED);
        offline.setResolvedAt(now.plusSeconds(300));
        alertRepository.saveAndFlush(offline);

        assertThat(alertRepository.findFirstByInverter_IdAndTypeAndStatusOrderByTriggeredAtDesc(
                inverter.getId(), AlertType.INVERTER_OFFLINE, AlertStatus.ACTIVE)).isEmpty();
        assertThat(alertRepository.findAllByStatusOrderByTriggeredAtDesc(AlertStatus.RESOLVED)).hasSize(1);
    }

    @Test
    void weatherForecastAndObservationRoundTrip() {
        var plant = plantRepository.findAll().get(0);
        Instant noon = Instant.parse("2026-07-10T15:00:00Z");

        weatherRepository.saveAndFlush(Weather.builder()
                .plant(plant)
                .observedAt(noon)
                .temperatureC(new BigDecimal("27.5"))
                .condition("clear")
                .cloudCoverPct(new BigDecimal("10.00"))
                .forecast(true)
                .expectedGenerationKwh(new BigDecimal("48.200"))
                .build());

        weatherRepository.saveAndFlush(Weather.builder()
                .plant(plant)
                .observedAt(noon)
                .temperatureC(new BigDecimal("26.1"))
                .condition("clouds")
                .cloudCoverPct(new BigDecimal("35.00"))
                .forecast(false)
                .build());

        assertThat(weatherRepository.findAllByPlant_IdAndForecastAndObservedAtBetweenOrderByObservedAt(
                plant.getId(), true, noon.minusSeconds(3600), noon.plusSeconds(3600)))
                .hasSize(1)
                .first()
                .satisfies(w -> assertThat(w.getExpectedGenerationKwh()).isEqualByComparingTo("48.200"));

        assertThat(weatherRepository.findFirstByPlant_IdAndForecastFalseOrderByObservedAtDesc(plant.getId()))
                .hasValueSatisfying(w -> assertThat(w.getCondition()).isEqualTo("clouds"));
    }
}
