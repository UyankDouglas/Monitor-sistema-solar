package com.solarmonitor.weather.service;

import com.solarmonitor.config.service.ConfigurationService;
import com.solarmonitor.energy.repository.DailyGenerationRepository;
import com.solarmonitor.plant.domain.Inverter;
import com.solarmonitor.plant.domain.Plant;
import com.solarmonitor.plant.repository.InverterRepository;
import com.solarmonitor.plant.repository.PlantRepository;
import com.solarmonitor.weather.domain.Weather;
import com.solarmonitor.weather.repository.WeatherRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Clima e comparação previsto × real.
 *
 * <p>Estimativa de geração: {@code radiação(kWh/m²) × capacidade(kWp) × PR},
 * onde PR = {@value #PERFORMANCE_RATIO} (performance ratio típico de sistemas
 * residenciais bem instalados). A radiação vem da Open-Meteo em MJ/m²
 * (÷ 3,6 → kWh/m²). A capacidade usa {@code plants.installed_capacity_kwp}
 * ou, na falta, a potência nominal do primeiro inversor.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WeatherService {

    static final BigDecimal PERFORMANCE_RATIO = new BigDecimal("0.80");
    private static final BigDecimal MJ_TO_KWH = new BigDecimal("3.6");

    private final OpenMeteoClient client;
    private final ConfigurationService configurations;
    private final PlantRepository plantRepository;
    private final InverterRepository inverterRepository;
    private final WeatherRepository weatherRepository;
    private final DailyGenerationRepository dailyRepository;
    private final TransactionTemplate transactionTemplate;

    /** Coordenadas configuradas (tela de Configurações, grupo Clima). */
    public Optional<BigDecimal[]> coordinates() {
        var lat = configurations.getDecimal("weather.latitude");
        var lon = configurations.getDecimal("weather.longitude");
        if (lat.isEmpty() || lon.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new BigDecimal[]{lat.get(), lon.get()});
    }

    public boolean enabled() {
        return configurations.getString("weather.enabled").map(Boolean::parseBoolean).orElse(true);
    }

    /**
     * Busca previsão na Open-Meteo e persiste observação + previsões diárias.
     * A chamada HTTP fica FORA de transação (não segura conexão do pool
     * durante rede externa); só a persistência roda transacional.
     */
    public void refresh() {
        var coords = coordinates();
        if (!enabled() || coords.isEmpty()) {
            log.debug("Clima desabilitado ou sem coordenadas configuradas; pulando");
            return;
        }
        Plant plant = plantRepository.findAll().stream().findFirst().orElse(null);
        if (plant == null) {
            return;
        }
        var response = client.forecast(coords.get()[0], coords.get()[1], 7, 7);
        if (response == null || response.daily() == null || response.daily().time() == null) {
            log.warn("Open-Meteo respondeu sem bloco daily; nada persistido");
            return;
        }
        transactionTemplate.executeWithoutResult(tx -> persist(plant, response));
    }

    private void persist(Plant plant, OpenMeteoClient.ForecastResponse response) {
        // Fuso da COORDENADA (retornado pela API): âncoras dos dias precisam
        // ser função pura do local da usina — usar o app.timezone de exibição
        // duplicaria previsões se o usuário o alterasse.
        ZoneId zone = zoneOf(response, plant);
        BigDecimal capacityKwp = capacityKwp(plant);

        // Observação corrente (uma por hora — upsert pela chave natural).
        if (response.current() != null && response.current().time() != null) {
            Instant observedAt = java.time.LocalDateTime.parse(response.current().time())
                    .atZone(zone).toInstant();
            upsert(plant, observedAt, false, w -> {
                w.setTemperatureC(response.current().temperatureC());
                w.setCloudCoverPct(response.current().cloudCoverPct());
                w.setCondition(OpenMeteoClient.describe(response.current().weatherCode()));
            });
        }

        // Previsões diárias (ancoradas ao meio-dia local do dia).
        var daily = response.daily();
        for (int i = 0; i < daily.time().size(); i++) {
            LocalDate date = LocalDate.parse(daily.time().get(i));
            Instant anchor = date.atTime(12, 0).atZone(zone).toInstant();
            BigDecimal radiationMj = at(daily.radiationMjM2(), i);
            BigDecimal expected = expectedKwh(radiationMj, capacityKwp);
            int index = i;
            upsert(plant, anchor, true, w -> {
                w.setCondition(OpenMeteoClient.describe(at(daily.weatherCode(), index)));
                w.setTemperatureC(at(daily.tempMaxC(), index));
                w.setCloudCoverPct(at(daily.cloudCoverPct(), index));
                w.setIrradianceWM2(radiationMj == null ? null
                        : radiationMj.divide(MJ_TO_KWH, 1, RoundingMode.HALF_UP)
                                .multiply(BigDecimal.valueOf(1000)).setScale(1, RoundingMode.HALF_UP));
                w.setExpectedGenerationKwh(expected);
            });
        }
        log.info("Clima atualizado: {} dias de previsão persistidos", daily.time().size());
    }

    private ZoneId zoneOf(OpenMeteoClient.ForecastResponse response, Plant plant) {
        if (response.timezone() != null) {
            try {
                return ZoneId.of(response.timezone());
            } catch (java.time.DateTimeException e) {
                log.warn("Timezone da Open-Meteo inválido ('{}'); usando o configurado",
                        response.timezone());
            }
        }
        return configurations.getZone(plant);
    }

    /** Resumo consumido pela tela Clima: previsão × geração real por dia. */
    @Transactional(readOnly = true)
    public WeatherSummary summary() {
        if (!enabled()) {
            return new WeatherSummary(false,
                    "Atualização de clima desabilitada — habilite weather.enabled em Configurações → Clima",
                    null, List.of());
        }
        var coords = coordinates();
        Plant plant = plantRepository.findAll().stream().findFirst().orElse(null);
        if (plant == null || coords.isEmpty()) {
            return new WeatherSummary(false,
                    "Configure weather.latitude e weather.longitude em Configurações → Clima",
                    null, List.of());
        }
        ZoneId zone = configurations.getZone(plant);
        LocalDate today = LocalDate.now(zone);
        Instant from = today.minusDays(7).atStartOfDay(zone).toInstant();
        Instant to = today.plusDays(8).atStartOfDay(zone).toInstant();

        var forecasts = weatherRepository
                .findAllByPlant_IdAndForecastAndObservedAtBetweenOrderByObservedAt(
                        plant.getId(), true, from, to);
        var latestObservation = weatherRepository
                .findFirstByPlant_IdAndForecastFalseOrderByObservedAtDesc(plant.getId())
                .orElse(null);

        Long inverterId = inverterRepository.findAllByPlant_Id(plant.getId()).stream()
                .findFirst().map(Inverter::getId).orElse(null);

        List<DaySummary> days = new ArrayList<>();
        for (Weather forecast : forecasts) {
            LocalDate date = LocalDate.ofInstant(forecast.getObservedAt(), zone);
            BigDecimal actual = inverterId == null ? null
                    : dailyRepository.findByInverter_IdAndGenerationDate(inverterId, date)
                            .map(d -> d.getEnergyKwh()).orElse(null);
            BigDecimal expected = forecast.getExpectedGenerationKwh();
            BigDecimal deviation = null;
            if (actual != null && expected != null && expected.signum() > 0) {
                deviation = actual.subtract(expected)
                        .multiply(BigDecimal.valueOf(100))
                        .divide(expected, 1, RoundingMode.HALF_UP);
            }
            days.add(new DaySummary(date, forecast.getCondition(), forecast.getTemperatureC(),
                    forecast.getCloudCoverPct(), expected, actual, deviation));
        }

        CurrentWeather current = latestObservation == null ? null
                : new CurrentWeather(latestObservation.getObservedAt(),
                        latestObservation.getTemperatureC(),
                        latestObservation.getCloudCoverPct(),
                        latestObservation.getCondition());
        return new WeatherSummary(true, null, current, days);
    }

    static BigDecimal expectedKwh(BigDecimal radiationMjM2, BigDecimal capacityKwp) {
        if (radiationMjM2 == null || capacityKwp == null || capacityKwp.signum() <= 0) {
            return null;
        }
        return radiationMjM2.divide(MJ_TO_KWH, 4, RoundingMode.HALF_UP)  // kWh/m²
                .multiply(capacityKwp)
                .multiply(PERFORMANCE_RATIO)
                .setScale(3, RoundingMode.HALF_UP);
    }

    private BigDecimal capacityKwp(Plant plant) {
        if (plant.getInstalledCapacityKwp() != null && plant.getInstalledCapacityKwp().signum() > 0) {
            return plant.getInstalledCapacityKwp();
        }
        return inverterRepository.findAllByPlant_Id(plant.getId()).stream()
                .findFirst()
                .map(inv -> inv.getRatedPowerW() == null ? null
                        : BigDecimal.valueOf(inv.getRatedPowerW()).movePointLeft(3))
                .orElse(null);
    }

    private void upsert(Plant plant, Instant observedAt, boolean forecast,
                        java.util.function.Consumer<Weather> mutator) {
        Weather row = findExisting(plant, observedAt, forecast)
                .orElseGet(() -> Weather.builder()
                        .plant(plant)
                        .observedAt(observedAt)
                        .forecast(forecast)
                        .build());
        mutator.accept(row);
        try {
            weatherRepository.save(row);
        } catch (DataIntegrityViolationException e) {
            // Corrida scheduler × refresh manual: o outro lado inseriu a
            // mesma chave natural primeiro — atualiza a linha vencedora.
            Weather winner = findExisting(plant, observedAt, forecast).orElseThrow(() -> e);
            mutator.accept(winner);
            weatherRepository.save(winner);
        }
    }

    private Optional<Weather> findExisting(Plant plant, Instant observedAt, boolean forecast) {
        return weatherRepository
                .findAllByPlant_IdAndForecastAndObservedAtBetweenOrderByObservedAt(
                        plant.getId(), forecast, observedAt, observedAt.plusMillis(1))
                .stream().findFirst();
    }

    private <T> T at(List<T> list, int index) {
        return list == null || index >= list.size() ? null : list.get(index);
    }

    // --- DTOs do resumo ---------------------------------------------------

    public record WeatherSummary(boolean available, String reason,
                                 CurrentWeather current, List<DaySummary> days) {
    }

    public record CurrentWeather(Instant observedAt, BigDecimal temperatureC,
                                 BigDecimal cloudCoverPct, String condition) {
    }

    public record DaySummary(LocalDate date, String condition, BigDecimal tempMaxC,
                             BigDecimal cloudCoverPct, BigDecimal expectedKwh,
                             BigDecimal actualKwh, BigDecimal deviationPct) {
    }
}
