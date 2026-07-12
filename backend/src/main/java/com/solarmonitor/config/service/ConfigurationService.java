package com.solarmonitor.config.service;

import com.solarmonitor.common.util.TimeZones;
import com.solarmonitor.config.domain.ConfigScope;
import com.solarmonitor.config.repository.ConfigurationRepository;
import com.solarmonitor.plant.domain.Plant;
import com.solarmonitor.provider.ProviderMode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.Locale;
import java.util.Optional;

/**
 * Acesso tipado às configurações globais persistidas (tabela
 * {@code configurations}, seeds na V8/V9). Leituras vão direto ao banco —
 * são baratas (índice único parcial) e garantem que alterações feitas pela
 * tela de Configurações valham no ciclo seguinte do scheduler, sem cache
 * para invalidar.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class ConfigurationService {

    public static final String KEY_READING_INTERVAL_MS = "scheduler.reading-interval-ms";
    public static final String KEY_PROVIDER_MODE = "provider.mode";
    public static final String KEY_KWH_PRICE = "energy.kwh-price";
    public static final String KEY_CO2_FACTOR = "energy.co2-factor-kg-per-kwh";
    public static final String KEY_CURRENCY = "energy.currency";
    public static final String KEY_TIMEZONE = "app.timezone";

    private final ConfigurationRepository repository;

    public Optional<String> getString(String key) {
        return repository.findByScopeAndKey(ConfigScope.GLOBAL, key)
                .map(cfg -> cfg.getValue() == null ? null : cfg.getValue().trim())
                .filter(v -> !v.isEmpty());
    }

    public Optional<Integer> getInt(String key) {
        return getString(key).flatMap(v -> parse(key, v, Integer::valueOf));
    }

    public Optional<Long> getLong(String key) {
        return getString(key).flatMap(v -> parse(key, v, Long::valueOf));
    }

    public Optional<BigDecimal> getDecimal(String key) {
        return getString(key).flatMap(v -> parse(key, v, BigDecimal::new));
    }

    /** Intervalo de leitura do scheduler; padrão 5000 ms, piso de 1000 ms. */
    public long getReadingIntervalMs() {
        long interval = getLong(KEY_READING_INTERVAL_MS).orElse(5_000L);
        return Math.max(interval, 1_000L);
    }

    /**
     * Parâmetros econômicos e de fuso: a CONFIGURAÇÃO (editável na tela)
     * vence; a coluna da planta é o fallback de fábrica. Sem isto, a tela de
     * Configurações ofereceria chaves que ninguém consome.
     */
    public BigDecimal getKwhPrice(Plant plant) {
        return getDecimal(KEY_KWH_PRICE).orElse(plant.getKwhPrice());
    }

    public BigDecimal getCo2Factor(Plant plant) {
        return getDecimal(KEY_CO2_FACTOR).orElse(plant.getCo2FactorKgPerKwh());
    }

    public String getCurrency(Plant plant) {
        return getString(KEY_CURRENCY).orElse(plant.getCurrency());
    }

    public ZoneId getZone(Plant plant) {
        return getString(KEY_TIMEZONE)
                .map(tz -> {
                    try {
                        return ZoneId.of(tz);
                    } catch (DateTimeException e) {
                        log.warn("app.timezone inválido ('{}'); usando o fuso da planta", tz);
                        return null;
                    }
                })
                .filter(java.util.Objects::nonNull)
                .orElseGet(() -> TimeZones.of(plant));
    }

    /** Modo de coleta ativo; valores inválidos caem em SIMULATED com aviso. */
    public ProviderMode getProviderMode() {
        String raw = getString(KEY_PROVIDER_MODE).orElse(ProviderMode.SIMULATED.name());
        try {
            return ProviderMode.valueOf(raw.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            log.warn("provider.mode inválido ('{}'); usando SIMULATED", raw);
            return ProviderMode.SIMULATED;
        }
    }

    private <T> Optional<T> parse(String key, String value, java.util.function.Function<String, T> parser) {
        try {
            return Optional.of(parser.apply(value));
        } catch (NumberFormatException e) {
            log.warn("Configuração '{}' com valor não numérico ('{}'); ignorando", key, value);
            return Optional.empty();
        }
    }
}
