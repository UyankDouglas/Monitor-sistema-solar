package com.solarmonitor.common.util;

import com.solarmonitor.plant.domain.Plant;

import java.time.DateTimeException;
import java.time.ZoneId;

/** Resolução do fuso horário da planta com fallback seguro. */
public final class TimeZones {

    public static final ZoneId FALLBACK = ZoneId.of("America/Sao_Paulo");

    private TimeZones() {
    }

    /** Fuso da planta; se o valor persistido for inválido, usa o fallback. */
    public static ZoneId of(Plant plant) {
        try {
            return ZoneId.of(plant.getTimezone());
        } catch (DateTimeException e) {
            return FALLBACK;
        }
    }
}
