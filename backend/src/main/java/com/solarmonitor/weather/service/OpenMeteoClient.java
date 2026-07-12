package com.solarmonitor.weather.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.util.List;

/**
 * Cliente da Open-Meteo (https://open-meteo.com) — previsão gratuita, sem
 * chave de API. Radiação solar diária ({@code shortwave_radiation_sum}, em
 * MJ/m²) é a base da estimativa de geração.
 */
@Component
public class OpenMeteoClient {

    private final RestClient restClient;

    public OpenMeteoClient(RestClient.Builder builder,
                           @Value("${app.weather.base-url}") String baseUrl) {
        this.restClient = builder.baseUrl(baseUrl).build();
    }

    /** Últimos {@code pastDays} + próximos {@code forecastDays} dias. */
    public ForecastResponse forecast(BigDecimal latitude, BigDecimal longitude,
                                     int pastDays, int forecastDays) {
        try {
            return restClient.get()
                    .uri(uri -> uri.path("/v1/forecast")
                            .queryParam("latitude", latitude)
                            .queryParam("longitude", longitude)
                            .queryParam("current", "temperature_2m,cloud_cover,weather_code")
                            .queryParam("daily", "weather_code,temperature_2m_max,temperature_2m_min,"
                                    + "cloud_cover_mean,shortwave_radiation_sum")
                            .queryParam("timezone", "auto")
                            .queryParam("past_days", pastDays)
                            .queryParam("forecast_days", forecastDays)
                            .build())
                    .retrieve()
                    .body(ForecastResponse.class);
        } catch (RestClientException e) {
            throw new IllegalStateException("Falha ao consultar a Open-Meteo: " + e.getMessage(), e);
        }
    }

    // --- DTOs ------------------------------------------------------------

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ForecastResponse(Current current, Daily daily, String timezone) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Current(
            String time,
            @JsonProperty("temperature_2m") BigDecimal temperatureC,
            @JsonProperty("cloud_cover") BigDecimal cloudCoverPct,
            @JsonProperty("weather_code") Integer weatherCode) {
    }

    /** Arrays paralelos indexados por dia — formato nativo da Open-Meteo. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Daily(
            List<String> time,
            @JsonProperty("weather_code") List<Integer> weatherCode,
            @JsonProperty("temperature_2m_max") List<BigDecimal> tempMaxC,
            @JsonProperty("temperature_2m_min") List<BigDecimal> tempMinC,
            @JsonProperty("cloud_cover_mean") List<BigDecimal> cloudCoverPct,
            @JsonProperty("shortwave_radiation_sum") List<BigDecimal> radiationMjM2) {
    }

    /** Códigos WMO → descrição em pt-BR (subconjunto relevante). */
    public static String describe(Integer wmoCode) {
        if (wmoCode == null) {
            return "—";
        }
        return switch (wmoCode) {
            case 0 -> "Céu limpo";
            case 1, 2 -> "Parcialmente nublado";
            case 3 -> "Nublado";
            case 45, 48 -> "Nevoeiro";
            case 51, 53, 55, 56, 57 -> "Garoa";
            case 61, 63, 65, 66, 67 -> "Chuva";
            case 71, 73, 75, 77 -> "Neve";
            case 80, 81, 82 -> "Pancadas de chuva";
            case 95, 96, 99 -> "Tempestade";
            default -> "Código " + wmoCode;
        };
    }
}
