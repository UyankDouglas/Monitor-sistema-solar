package com.solarmonitor.config.web;

import java.time.Instant;

/**
 * Configuração global exposta pela API. {@code secret=true} indica valor
 * mascarado no GET (senhas/segredos nunca voltam em claro).
 */
public record ConfigurationDto(
        String key,
        String value,
        String valueType,
        boolean secret,
        Instant updatedAt) {
}
