package com.solarmonitor.config.service;

import com.solarmonitor.config.domain.ConfigScope;
import com.solarmonitor.config.domain.Configuration;
import com.solarmonitor.config.repository.ConfigurationRepository;
import com.solarmonitor.config.web.ConfigurationDto;
import com.solarmonitor.provider.ProviderMode;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Gestão das configurações globais pela API. Regras:
 * <ul>
 *   <li>só chaves existentes (seeds V8/V9) podem ser alteradas — a tela de
 *       Configurações não inventa chaves;</li>
 *   <li>valores validados pelo {@code value_type} e por regras específicas
 *       (intervalo mínimo, modo de provider válido etc.);</li>
 *   <li>segredos (chave contendo "secret" ou "password") voltam mascarados
 *       no GET e nunca aparecem em log.</li>
 * </ul>
 * Efeito é imediato no ciclo seguinte do scheduler — nada exige restart.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SettingsService {

    static final String MASK = "••••••••";
    private static final Set<String> VALID_BOOLEANS = Set.of("true", "false");

    private final ConfigurationRepository repository;

    @Transactional(readOnly = true)
    public List<ConfigurationDto> list() {
        return repository.findAllByScope(ConfigScope.GLOBAL).stream()
                .sorted(Comparator.comparing(Configuration::getKey))
                .map(this::toDto)
                .toList();
    }

    @Transactional
    public ConfigurationDto update(String key, String rawValue) {
        Configuration config = repository.findByScopeAndKey(ConfigScope.GLOBAL, key)
                .orElseThrow(() -> new EntityNotFoundException("Configuração '" + key + "' não existe"));
        String value = rawValue == null ? "" : rawValue.trim();

        validateType(config, value);
        validateBusinessRules(key, value);

        config.setValue(value);
        log.info("Configuração '{}' atualizada{}", key,
                isSecret(key) ? "" : " para '" + value + "'");
        return toDto(config);
    }

    private void validateType(Configuration config, String value) {
        if (value.isEmpty()) {
            return; // limpar um valor é permitido (ex.: remover credencial)
        }
        switch (config.getValueType()) {
            case INT -> {
                try {
                    Long.parseLong(value);
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException(
                            "'" + config.getKey() + "' exige valor inteiro; recebido: '" + value + "'");
                }
            }
            case DECIMAL -> {
                try {
                    new BigDecimal(value);
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException(
                            "'" + config.getKey() + "' exige valor decimal; recebido: '" + value + "'");
                }
            }
            case BOOLEAN -> {
                if (!VALID_BOOLEANS.contains(value.toLowerCase(Locale.ROOT))) {
                    throw new IllegalArgumentException(
                            "'" + config.getKey() + "' exige true/false; recebido: '" + value + "'");
                }
            }
            case STRING, JSON -> { /* livre */ }
        }
    }

    private void validateBusinessRules(String key, String value) {
        switch (key) {
            case ConfigurationService.KEY_READING_INTERVAL_MS -> {
                long interval = Long.parseLong(value);
                if (interval < 1_000 || interval > 3_600_000) {
                    throw new IllegalArgumentException(
                            "Intervalo de leitura deve estar entre 1000 ms (1 s) e 3600000 ms (1 h)");
                }
            }
            case ConfigurationService.KEY_PROVIDER_MODE -> {
                try {
                    ProviderMode.valueOf(value.toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException e) {
                    throw new IllegalArgumentException("provider.mode deve ser um de: CLOUD, LOCAL, SIMULATED");
                }
            }
            case "energy.kwh-price", "energy.co2-factor-kg-per-kwh" -> {
                if (!value.isEmpty() && new BigDecimal(value).signum() < 0) {
                    throw new IllegalArgumentException("'" + key + "' não pode ser negativo");
                }
            }
            default -> { /* sem regra extra */ }
        }
    }

    private ConfigurationDto toDto(Configuration config) {
        boolean secret = isSecret(config.getKey());
        String value = config.getValue();
        return new ConfigurationDto(
                config.getKey(),
                secret && value != null && !value.isEmpty() ? MASK : value,
                config.getValueType().name(),
                secret,
                config.getUpdatedAt());
    }

    private boolean isSecret(String key) {
        String lower = key.toLowerCase(Locale.ROOT);
        return lower.contains("secret") || lower.contains("password");
    }
}
