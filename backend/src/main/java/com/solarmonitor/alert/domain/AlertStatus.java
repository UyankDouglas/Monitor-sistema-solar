package com.solarmonitor.alert.domain;

/** Ciclo de vida de um alerta: ACTIVE → ACKNOWLEDGED → RESOLVED. */
public enum AlertStatus {
    ACTIVE,
    ACKNOWLEDGED,
    RESOLVED
}
