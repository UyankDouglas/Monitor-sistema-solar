package com.solarmonitor.plant.domain;

/** Estado operacional do inversor, também gravado em cada amostra de telemetria. */
public enum InverterStatus {
    ONLINE,
    OFFLINE,
    FAULT,
    STANDBY,
    UNKNOWN
}
