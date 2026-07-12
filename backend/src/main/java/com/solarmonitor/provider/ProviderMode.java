package com.solarmonitor.provider;

/**
 * Origem de dados ativa, definida pela configuração global
 * {@code provider.mode} (editável em runtime na tela de Configurações).
 *
 * <p>Distinto de {@code plant.domain.ProviderType} (atributo do inversor,
 * CLOUD/LOCAL): aqui é o roteamento efetivo da coleta, que inclui o modo
 * {@code SIMULATED} para desenvolvimento e demonstração sem hardware.</p>
 */
public enum ProviderMode {
    CLOUD,
    LOCAL,
    SIMULATED
}
