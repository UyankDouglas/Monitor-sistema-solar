package com.solarmonitor.ingestion;

import com.solarmonitor.config.service.ConfigurationService;
import com.solarmonitor.provider.EnergyProvider;
import com.solarmonitor.provider.ProviderMode;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Resolve o {@link EnergyProvider} ativo a partir da configuração global
 * {@code provider.mode} — o coração da troca de origem sem tocar no resto da
 * aplicação. Novos providers entram automaticamente: basta implementar a
 * interface e declarar o bean.
 */
@Component
public class ProviderRouter {

    private final ConfigurationService configurations;
    private final Map<ProviderMode, EnergyProvider> providersByMode = new EnumMap<>(ProviderMode.class);

    public ProviderRouter(ConfigurationService configurations, List<EnergyProvider> providers) {
        this.configurations = configurations;
        for (EnergyProvider provider : providers) {
            EnergyProvider previous = providersByMode.put(provider.mode(), provider);
            if (previous != null) {
                throw new IllegalStateException("Dois providers para o modo " + provider.mode()
                        + ": " + previous.getClass().getSimpleName()
                        + " e " + provider.getClass().getSimpleName());
            }
        }
    }

    /** Provider correspondente ao {@code provider.mode} atual. */
    public EnergyProvider active() {
        ProviderMode mode = configurations.getProviderMode();
        EnergyProvider provider = providersByMode.get(mode);
        if (provider == null) {
            throw new IllegalStateException("Nenhum provider registrado para o modo " + mode);
        }
        return provider;
    }
}
