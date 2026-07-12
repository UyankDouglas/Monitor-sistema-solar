package com.solarmonitor.ingestion;

import com.solarmonitor.plant.domain.Inverter;
import com.solarmonitor.plant.domain.InverterStatus;
import com.solarmonitor.plant.repository.InverterRepository;
import com.solarmonitor.provider.EnergyProvider;
import com.solarmonitor.provider.EnergyReading;
import com.solarmonitor.provider.ProviderException;
import com.solarmonitor.realtime.RealtimePublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Orquestra um ciclo de coleta: para cada inversor, lê do provider ativo,
 * persiste (via {@link IngestionPersister}, atomicamente), e publica em tempo
 * real via WebSocket — a publicação fica fora da transação de propósito: um
 * broker indisponível não pode desfazer uma amostra já medida.
 *
 * <p>Falhas são isoladas por inversor e contabilizadas: após
 * {@value #OFFLINE_AFTER_FAILURES} falhas consecutivas o inversor é marcado
 * OFFLINE — insumo do alerta INVERTER_OFFLINE (etapa de alertas).</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class IngestionService {

    static final int OFFLINE_AFTER_FAILURES = 3;

    private final ProviderRouter providerRouter;
    private final IngestionPersister persister;
    private final InverterRepository inverterRepository;
    private final RealtimePublisher realtimePublisher;

    private final Map<Long, AtomicInteger> consecutiveFailures = new ConcurrentHashMap<>();
    private final java.util.Set<Long> succeededSinceStartup = java.util.concurrent.ConcurrentHashMap.newKeySet();

    /** Falhas consecutivas de coleta — insumo do alerta COMMUNICATION_LOSS. */
    public int consecutiveFailures(Long inverterId) {
        AtomicInteger counter = consecutiveFailures.get(inverterId);
        return counter == null ? 0 : counter.get();
    }

    /**
     * Evidência POSITIVA de coleta bem-sucedida desde o start do processo.
     * Distingue "contador zerado porque voltou a funcionar" de "contador
     * zerado porque o backend reiniciou durante a indisponibilidade".
     */
    public boolean hasSucceededSinceStartup(Long inverterId) {
        return succeededSinceStartup.contains(inverterId);
    }

    /** Um ciclo completo sobre todos os inversores cadastrados. */
    public void ingestAll() {
        List<Inverter> inverters = inverterRepository.findAll();
        if (inverters.isEmpty()) {
            log.debug("Nenhum inversor cadastrado; ciclo de ingestão vazio");
            return;
        }
        EnergyProvider provider = providerRouter.active();
        for (Inverter inverter : inverters) {
            try {
                ingest(provider, inverter);
            } catch (ProviderException e) {
                registerFailure(inverter, e);
            } catch (RuntimeException e) {
                // Bug interno não pode derrubar o loop dos demais inversores.
                log.error("Erro inesperado ingerindo inversor {}: {}", inverter.getId(), e.getMessage(), e);
            }
        }
    }

    private void ingest(EnergyProvider provider, Inverter inverter) throws ProviderException {
        EnergyReading reading = provider.read(inverter);
        persister.persist(reading, inverter.getId());
        consecutiveFailures.remove(inverter.getId());
        succeededSinceStartup.add(inverter.getId());
        realtimePublisher.publishReading(inverter.getId(), reading);

        if (log.isDebugEnabled()) {
            log.debug("Inversor {}: {} W AC, SOC {}%", inverter.getId(),
                    reading.acPowerW(), reading.batterySocPct());
        }
    }

    private void registerFailure(Inverter inverter, ProviderException e) {
        int failures = consecutiveFailures
                .computeIfAbsent(inverter.getId(), id -> new AtomicInteger())
                .incrementAndGet();
        log.warn("Falha de coleta no inversor {} ({}ª consecutiva): {}",
                inverter.getId(), failures, e.getMessage());
        if (failures >= OFFLINE_AFTER_FAILURES && inverter.getStatus() != InverterStatus.OFFLINE) {
            persister.markOffline(inverter.getId());
            log.warn("Inversor {} marcado OFFLINE após {} falhas consecutivas", inverter.getId(),
                    OFFLINE_AFTER_FAILURES);
        }
    }
}
