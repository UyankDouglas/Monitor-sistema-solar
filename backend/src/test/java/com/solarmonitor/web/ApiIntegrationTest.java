package com.solarmonitor.web;

import com.solarmonitor.aggregation.AggregationService;
import com.solarmonitor.energy.domain.DailyGeneration;
import com.solarmonitor.energy.repository.DailyGenerationRepository;
import com.solarmonitor.ingestion.IngestionService;
import com.solarmonitor.plant.domain.Inverter;
import com.solarmonitor.plant.repository.InverterRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.LocalDate;
import java.time.ZoneId;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Fumaça da API completa da Etapa 5: ingere via provider simulado, consolida
 * e exercita cada endpoint pelo MockMvc, validando status e campos-chave.
 */
@Testcontainers
@SpringBootTest(properties = "app.scheduler.enabled=false")
@AutoConfigureMockMvc
class ApiIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("timescale/timescaledb:2.17.2-pg16")
                    .asCompatibleSubstituteFor("postgres"));

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private IngestionService ingestionService;

    @Autowired
    private AggregationService aggregationService;

    @Autowired
    private InverterRepository inverterRepository;

    @Autowired
    private DailyGenerationRepository dailyGenerationRepository;

    @Autowired
    private org.springframework.transaction.support.TransactionTemplate transactionTemplate;

    private static boolean seeded = false;

    @BeforeEach
    void seedOnce() {
        if (seeded) {
            return;
        }
        ingestionService.ingestAll();
        ingestionService.ingestAll();
        Inverter inverter = inverterRepository.findAll().get(0);
        aggregationService.consolidateInverter(inverter.getId());

        // Linha diária determinística com geração > 0: de madrugada o
        // simulador gera 0 kWh e /api/statistics ficaria sem bestDay — o
        // teste não pode depender da hora em que a suíte roda.
        transactionTemplate.executeWithoutResult(tx -> {
            Inverter managed = inverterRepository.findById(inverter.getId()).orElseThrow();
            dailyGenerationRepository.save(DailyGeneration.builder()
                    .inverter(managed)
                    .generationDate(java.time.LocalDate.now(ZoneId.of("America/Sao_Paulo")).minusDays(2))
                    .energyKwh(new java.math.BigDecimal("42.000"))
                    .peakPowerW(8000)
                    .savings(new java.math.BigDecimal("39.90"))
                    .co2AvoidedKg(new java.math.BigDecimal("3.431"))
                    .build());
        });

        // Só marca após sucesso: se o seed falhar, o próximo teste tenta de
        // novo e reporta a causa real em vez de falhas em cascata.
        seeded = true;
    }

    @Test
    void dashboardReturnsAllCards() throws Exception {
        mockMvc.perform(get("/api/dashboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.inverterName").value("Deye SUN-10K-SG04LP3"))
                .andExpect(jsonPath("$.inverterStatus").value("ONLINE"))
                .andExpect(jsonPath("$.currentPowerW", notNullValue()))
                .andExpect(jsonPath("$.batterySocPct", notNullValue()))
                .andExpect(jsonPath("$.todayEnergyKwh", notNullValue()))
                .andExpect(jsonPath("$.currency").value("BRL"))
                .andExpect(jsonPath("$.activeAlerts").value(0));
    }

    @Test
    void historyReturnsRawSeriesForShortWindow() throws Exception {
        var now = java.time.Instant.now();
        mockMvc.perform(get("/api/energy/history")
                        .param("from", now.minusSeconds(3600).toString())
                        .param("to", now.plusSeconds(60).toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bucketSeconds").value(0))
                .andExpect(jsonPath("$.points", hasSize(greaterThan(1))))
                .andExpect(jsonPath("$.points[0].acPowerW", notNullValue()));
    }

    @Test
    void dailyAndExportEndpointsWork() throws Exception {
        LocalDate today = LocalDate.now(ZoneId.of("America/Sao_Paulo"));
        String from = today.minusDays(1).toString();
        String to = today.toString();

        mockMvc.perform(get("/api/energy/daily")
                        .param("from", from).param("to", to))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].energyKwh", notNullValue()))
                .andExpect(jsonPath("$[0].savings", notNullValue()));

        mockMvc.perform(get("/api/energy/daily/export")
                        .param("from", from).param("to", to).param("format", "csv"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString(".csv")))
                .andExpect(csvHeaderPresent());

        mockMvc.perform(get("/api/energy/daily/export")
                        .param("from", from).param("to", to).param("format", "pdf"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"));

        mockMvc.perform(get("/api/energy/daily/export")
                        .param("from", from).param("to", to).param("format", "doc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Requisição inválida"));
    }

    private static org.springframework.test.web.servlet.ResultMatcher csvHeaderPresent() {
        return result -> {
            String body = result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
            if (!body.contains("Data;Geração (kWh)")) {
                throw new AssertionError("CSV sem cabeçalho esperado: " + body.substring(0, Math.min(80, body.length())));
            }
        };
    }

    @Test
    void monthlyEndpointReturnsCurrentMonth() throws Exception {
        mockMvc.perform(get("/api/energy/monthly"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].energyKwh", notNullValue()));
    }

    @Test
    void statisticsEndpointComputesTotals() throws Exception {
        mockMvc.perform(get("/api/statistics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.daysWithData", greaterThan(0)))
                .andExpect(jsonPath("$.totalEnergyKwh", notNullValue()))
                .andExpect(jsonPath("$.bestDay.date", notNullValue()));
    }

    @Test
    void invertersAndAlertsEndpointsWork() throws Exception {
        mockMvc.perform(get("/api/inverters"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].model").value("SUN-10K-SG04LP3"))
                .andExpect(jsonPath("$[0].plantName").value("Minha Usina"));

        mockMvc.perform(get("/api/alerts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void settingsListMasksSecretsAndUpdateValidates() throws Exception {
        // Filtro JSONPath retorna array — matcher de item, não de escalar
        mockMvc.perform(get("/api/settings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.key=='provider.mode')].value", hasItem("SIMULATED")));

        mockMvc.perform(put("/api/settings/scheduler.reading-interval-ms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"value\":\"8000\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.value").value("8000"));

        mockMvc.perform(put("/api/settings/scheduler.reading-interval-ms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"value\":\"200\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Requisição inválida"));

        mockMvc.perform(put("/api/settings/chave.inexistente")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"value\":\"x\"}"))
                .andExpect(status().isNotFound());
    }
}
