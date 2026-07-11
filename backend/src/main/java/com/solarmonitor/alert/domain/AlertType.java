package com.solarmonitor.alert.domain;

/** Tipos de alerta suportados (espelha o CHECK de {@code alerts.type}). */
public enum AlertType {
    /** Sem leituras há mais tempo que o limiar configurado. */
    INVERTER_OFFLINE,
    /** Potência abaixo do mínimo durante a janela solar. */
    NO_GENERATION_DAYTIME,
    /** Temperatura do inversor ou da bateria acima do limiar. */
    HIGH_TEMPERATURE,
    /** SOC da bateria abaixo do mínimo. */
    LOW_BATTERY,
    /** Inversor reportou estado de falha. */
    INVERTER_FAULT,
    /** Falhas consecutivas de comunicação com o provider. */
    COMMUNICATION_LOSS
}
