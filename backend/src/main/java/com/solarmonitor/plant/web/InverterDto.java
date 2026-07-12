package com.solarmonitor.plant.web;

import java.math.BigDecimal;
import java.time.Instant;

/** Inversor exposto pela API. */
public record InverterDto(
        Long id,
        String name,
        String serialNumber,
        String model,
        Integer ratedPowerW,
        Short phases,
        Short mpptCount,
        String firmwareVersion,
        String providerType,
        String status,
        Instant lastSeenAt,
        Long plantId,
        String plantName,
        String plantTimezone,
        BigDecimal installedCapacityKwp) {
}
