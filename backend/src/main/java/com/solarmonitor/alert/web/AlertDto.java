package com.solarmonitor.alert.web;

import java.time.Instant;
import java.util.Map;

/** Alerta exposto pela API. */
public record AlertDto(
        Long id,
        Long inverterId,
        String type,
        String severity,
        String status,
        String message,
        Map<String, Object> details,
        Instant triggeredAt,
        Instant acknowledgedAt,
        Instant resolvedAt) {
}
