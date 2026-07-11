package com.solarmonitor.plant.domain;

/**
 * Origem dos dados do inversor: API oficial Solarman ({@code CLOUD}) ou
 * comunicação direta com o logger na rede local, porta 8899 ({@code LOCAL}).
 */
public enum ProviderType {
    CLOUD,
    LOCAL
}
