package com.solarmonitor.provider;

import com.solarmonitor.plant.domain.Inverter;

/**
 * Abstração central de coleta de dados: qualquer origem capaz de produzir uma
 * {@link EnergyReading} para um inversor. Trocar a origem (nuvem Solarman,
 * logger local na porta 8899 ou simulador) não afeta o restante da aplicação —
 * o roteamento é feito pela configuração {@code provider.mode}.
 */
public interface EnergyProvider {

    /** Modo que esta implementação atende. */
    ProviderMode mode();

    /**
     * Lê o estado atual do inversor.
     *
     * @throws ProviderException em falha de comunicação, autenticação ou
     *                           resposta malformada — o scheduler trata e
     *                           contabiliza para o estado OFFLINE
     */
    EnergyReading read(Inverter inverter) throws ProviderException;
}
