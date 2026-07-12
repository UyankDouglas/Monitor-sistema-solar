package com.solarmonitor.weather.service;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class OpenMeteoClientTest {

    @Test
    void parsesCurrentAndDailyBlocks() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        OpenMeteoClient client = new OpenMeteoClient(builder, "https://meteo.test");

        server.expect(requestTo(org.hamcrest.Matchers.startsWith("https://meteo.test/v1/forecast")))
                .andExpect(queryParam("latitude", "-23.55"))
                .andExpect(queryParam("past_days", "7"))
                .andRespond(withSuccess("""
                        {
                          "timezone": "America/Sao_Paulo",
                          "current": {"time": "2026-07-12T09:00", "temperature_2m": 21.4,
                                      "cloud_cover": 35, "weather_code": 2},
                          "daily": {
                            "time": ["2026-07-12", "2026-07-13"],
                            "weather_code": [2, 61],
                            "temperature_2m_max": [24.1, 19.0],
                            "temperature_2m_min": [14.2, 13.1],
                            "cloud_cover_mean": [40, 90],
                            "shortwave_radiation_sum": [16.2, 5.4]
                          }
                        }
                        """, MediaType.APPLICATION_JSON));

        var response = client.forecast(new BigDecimal("-23.55"), new BigDecimal("-46.63"), 7, 7);

        assertThat(response.timezone()).isEqualTo("America/Sao_Paulo");
        assertThat(response.current().temperatureC()).isEqualByComparingTo("21.4");
        assertThat(response.current().weatherCode()).isEqualTo(2);
        assertThat(response.daily().time()).containsExactly("2026-07-12", "2026-07-13");
        assertThat(response.daily().radiationMjM2().get(1)).isEqualByComparingTo("5.4");
        server.verify();
    }
}
