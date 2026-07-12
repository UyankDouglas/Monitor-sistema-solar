package com.solarmonitor.alert.service;

import com.solarmonitor.alert.domain.Alert;
import com.solarmonitor.alert.domain.AlertRule;
import com.solarmonitor.alert.domain.AlertSeverity;
import com.solarmonitor.alert.domain.AlertStatus;
import com.solarmonitor.alert.domain.AlertType;
import com.solarmonitor.alert.repository.AlertRepository;
import com.solarmonitor.alert.repository.AlertRuleRepository;
import com.solarmonitor.config.service.ConfigurationService;
import com.solarmonitor.energy.domain.EnergySample;
import com.solarmonitor.energy.repository.EnergySampleRepository;
import com.solarmonitor.ingestion.IngestionService;
import com.solarmonitor.plant.domain.Inverter;
import com.solarmonitor.plant.domain.InverterStatus;
import com.solarmonitor.plant.repository.InverterRepository;
import com.solarmonitor.realtime.RealtimePublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Motor de alertas: avalia as regras habilitadas ({@code alert_rules},
 * limiares em JSONB) contra o estado corrente de cada inversor.
 *
 * <p>Comportamento por regra: dispara quando a condição vale e NÃO existe
 * alerta aberto (ACTIVE/ACKNOWLEDGED) do mesmo tipo — sem spam; e
 * auto-resolve os abertos quando a condição limpa. Cada disparo/resolução é
 * publicado em {@code /topic/alerts} para a interface reagir na hora.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AlertEngine {

    /** Estados considerados "abertos" para dedup e auto-resolução. */
    private static final Set<AlertStatus> OPEN = EnumSet.of(AlertStatus.ACTIVE, AlertStatus.ACKNOWLEDGED);
    /** Histerese do SOC: resolve só quando subir este tanto acima do limiar. */
    private static final int SOC_HYSTERESIS_PCT = 3;

    private final AlertRuleRepository ruleRepository;
    private final AlertRepository alertRepository;
    private final InverterRepository inverterRepository;
    private final EnergySampleRepository sampleRepository;
    private final ConfigurationService configurations;
    private final IngestionService ingestionService;
    private final RealtimePublisher realtimePublisher;

    /** Avalia todas as regras de um inversor. Chamar via proxy (transação). */
    @Transactional
    public void evaluate(Long inverterId) {
        Inverter inverter = inverterRepository.findById(inverterId).orElseThrow();
        EnergySample latest = sampleRepository
                .findFirstById_InverterIdOrderById_SampledAtDesc(inverterId)
                .orElse(null);

        for (AlertRule rule : ruleRepository.findAllByEnabledTrue()) {
            try {
                evaluateRule(rule, inverter, latest);
            } catch (RuntimeException e) {
                log.error("Regra {} falhou para o inversor {}: {}",
                        rule.getType(), inverterId, e.getMessage(), e);
            }
        }
    }

    private void evaluateRule(AlertRule rule, Inverter inverter, EnergySample latest) {
        Map<String, Object> t = rule.getThreshold() == null ? Map.of() : rule.getThreshold();
        switch (rule.getType()) {
            case INVERTER_OFFLINE -> {
                long limitSeconds = intOf(t, "offline_after_seconds", 120);
                boolean offline = inverter.getLastSeenAt() == null
                        || inverter.getLastSeenAt().isBefore(Instant.now().minusSeconds(limitSeconds));
                apply(rule.getType(), inverter, offline, AlertSeverity.CRITICAL,
                        "Sem leituras há mais de " + limitSeconds + " s",
                        Map.of("offline_after_seconds", limitSeconds));
            }
            case COMMUNICATION_LOSS -> {
                int maxFails = intOf(t, "max_failed_polls", 5);
                int failures = ingestionService.consecutiveFailures(inverter.getId());
                if (failures >= maxFails) {
                    fire(rule.getType(), inverter, AlertSeverity.WARNING,
                            failures + " falhas consecutivas de comunicação com o provider",
                            Map.of("failures", failures, "max_failed_polls", maxFails));
                } else if (ingestionService.hasSucceededSinceStartup(inverter.getId())) {
                    // Só resolve com evidência POSITIVA de sucesso: contador
                    // zerado por restart do processo não é comunicação de volta.
                    resolveOpen(rule.getType(), inverter);
                }
            }
            case INVERTER_FAULT -> {
                boolean fault = latest != null && latest.getInverterStatus() == InverterStatus.FAULT;
                apply(rule.getType(), inverter, fault, AlertSeverity.CRITICAL,
                        "Inversor reportou estado de FALHA", Map.of());
            }
            case HIGH_TEMPERATURE -> {
                if (latest == null || stale(latest)) {
                    return; // dado velho não resolve nem dispara — OFFLINE cuida disso
                }
                int invMax = intOf(t, "inverter_max_c", 65);
                int battMax = intOf(t, "battery_max_c", 45);
                BigDecimal invTemp = latest.getInverterTemperatureC();
                BigDecimal battTemp = latest.getBatteryTemperatureC();
                // Comparação decimal: intValue() truncaria 45,9 → 45 e o alerta
                // nunca dispararia até 1 °C inteiro acima do limiar.
                boolean hot = (invTemp != null && invTemp.compareTo(BigDecimal.valueOf(invMax)) > 0)
                        || (battTemp != null && battTemp.compareTo(BigDecimal.valueOf(battMax)) > 0);
                apply(rule.getType(), inverter, hot, AlertSeverity.WARNING,
                        "Temperatura alta — inversor: " + orDash(invTemp) + " °C, bateria: "
                                + orDash(battTemp) + " °C",
                        Map.of("inverter_c", String.valueOf(invTemp),
                                "battery_c", String.valueOf(battTemp),
                                "inverter_max_c", invMax, "battery_max_c", battMax));
            }
            case LOW_BATTERY -> {
                if (latest == null || stale(latest) || latest.getBatterySocPct() == null) {
                    return; // sem SOC confiável, não mexe no estado do alerta
                }
                int minSoc = intOf(t, "min_soc_pct", 15);
                int soc = latest.getBatterySocPct().intValue();
                if (soc < minSoc) {
                    fire(rule.getType(), inverter, AlertSeverity.WARNING,
                            "Bateria em " + soc + "% (mínimo " + minSoc + "%)",
                            Map.of("soc_pct", soc, "min_soc_pct", minSoc));
                } else if (soc >= minSoc + SOC_HYSTERESIS_PCT) {
                    // Histerese evita liga/desliga com o SOC oscilando no limiar.
                    resolveOpen(rule.getType(), inverter);
                }
            }
            case NO_GENERATION_DAYTIME -> evaluateNoGeneration(rule, t, inverter, latest);
        }
    }

    /** Sem geração dentro da janela solar, com carência após o início. */
    private void evaluateNoGeneration(AlertRule rule, Map<String, Object> t,
                                      Inverter inverter, EnergySample latest) {
        ZoneId zone = configurations.getZone(inverter.getPlant());
        LocalTime start = timeOf(t, "solar_window_start", LocalTime.of(8, 0));
        LocalTime end = timeOf(t, "solar_window_end", LocalTime.of(17, 0));
        int minPower = intOf(t, "min_power_w", 50);
        int graceMinutes = intOf(t, "grace_minutes", 30);

        ZonedDateTime now = ZonedDateTime.now(zone);
        LocalTime nowTime = now.toLocalTime();
        // Carência: só cobra geração depois de start+grace (nascer do sol lento).
        boolean inWindow = nowTime.isAfter(start.plusMinutes(graceMinutes)) && nowTime.isBefore(end);
        if (!inWindow) {
            resolveOpen(rule.getType(), inverter);
            return;
        }
        if (latest == null || stale(latest)) {
            return; // offline é assunto do INVERTER_OFFLINE
        }
        // Pico dos últimos grace_minutes: uma nuvem passageira não dispara.
        Instant from = Instant.now().minus(Duration.ofMinutes(graceMinutes));
        Integer maxRecent = sampleRepository.findSeries(inverter.getId(), from, Instant.now()).stream()
                .map(EnergySample::getAcPowerW)
                .filter(java.util.Objects::nonNull)
                .max(Integer::compareTo)
                .orElse(null);
        if (maxRecent == null) {
            return;
        }
        apply(rule.getType(), inverter, maxRecent < minPower, AlertSeverity.WARNING,
                "Sem geração no horário solar: pico de " + maxRecent + " W nos últimos "
                        + graceMinutes + " min (mínimo " + minPower + " W)",
                Map.of("max_recent_w", maxRecent, "min_power_w", minPower,
                        "window", start + "-" + end));
    }

    /** Dispara quando a condição vale; auto-resolve quando limpa. */
    private void apply(AlertType type, Inverter inverter, boolean condition,
                       AlertSeverity severity, String message, Map<String, Object> details) {
        if (condition) {
            fire(type, inverter, severity, message, details);
        } else {
            resolveOpen(type, inverter);
        }
    }

    private void fire(AlertType type, Inverter inverter, AlertSeverity severity,
                      String message, Map<String, Object> details) {
        boolean alreadyOpen = !alertRepository
                .findAllByInverter_IdAndTypeAndStatusIn(inverter.getId(), type, OPEN)
                .isEmpty();
        if (alreadyOpen) {
            return;
        }
        Alert alert = alertRepository.save(Alert.builder()
                .inverter(inverter)
                .type(type)
                .severity(severity)
                .message(message)
                .details(details)
                .triggeredAt(Instant.now())
                .build());
        log.warn("ALERTA disparado [{}] inversor {}: {}", type, inverter.getId(), message);
        publishAfterCommit("CREATED", alert);
    }

    private void resolveOpen(AlertType type, Inverter inverter) {
        List<Alert> open = alertRepository
                .findAllByInverter_IdAndTypeAndStatusIn(inverter.getId(), type, OPEN);
        for (Alert alert : open) {
            alert.setStatus(AlertStatus.RESOLVED);
            alert.setResolvedAt(Instant.now());
            log.info("Alerta [{}] do inversor {} auto-resolvido", type, inverter.getId());
            publishAfterCommit("RESOLVED", alert);
        }
    }

    /**
     * Publica só APÓS o commit: um rollback do ciclo (falha em regra
     * posterior) não pode deixar evento fantasma na interface — toast e badge
     * apontando para um alerta que não existe no banco.
     */
    private void publishAfterCommit(String action, Alert alert) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    realtimePublisher.publishAlert(action, alert);
                }
            });
        } else {
            realtimePublisher.publishAlert(action, alert);
        }
    }

    /**
     * Amostra velha não sustenta condição instantânea. O limiar acompanha o
     * intervalo de leitura configurado (2 ciclos, piso de 5 min) — com coleta
     * a cada 10 min, 5 min fixos fariam HIGH_TEMPERATURE oscilar entre
     * disparo e falso-resolve a cada ciclo.
     */
    private boolean stale(EnergySample sample) {
        long staleMs = Math.max(300_000L, 2 * configurations.getReadingIntervalMs());
        return sample.getId().getSampledAt().isBefore(Instant.now().minusMillis(staleMs));
    }

    private int intOf(Map<String, Object> map, String key, int fallback) {
        Object value = map.get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String s) {
            try {
                return Integer.parseInt(s.trim());
            } catch (NumberFormatException ignored) {
                // cai no fallback
            }
        }
        return fallback;
    }

    private LocalTime timeOf(Map<String, Object> map, String key, LocalTime fallback) {
        Object value = map.get(key);
        if (value instanceof String s) {
            try {
                return LocalTime.parse(s.trim());
            } catch (java.time.format.DateTimeParseException ignored) {
                // cai no fallback
            }
        }
        return fallback;
    }

    private String orDash(BigDecimal value) {
        return value == null ? "—" : value.toPlainString();
    }
}
