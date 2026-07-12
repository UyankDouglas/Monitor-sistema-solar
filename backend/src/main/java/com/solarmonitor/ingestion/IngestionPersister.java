package com.solarmonitor.ingestion;

import com.solarmonitor.energy.repository.EnergySampleRepository;
import com.solarmonitor.energy.repository.MpptReadingRepository;
import com.solarmonitor.plant.domain.Inverter;
import com.solarmonitor.plant.domain.InverterStatus;
import com.solarmonitor.plant.repository.InverterRepository;
import com.solarmonitor.provider.EnergyReading;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Fronteira transacional da ingestão — classe separada de propósito: se os
 * métodos {@code @Transactional} vivessem em {@link IngestionService} e fossem
 * chamados por auto-invocação, o proxy do Spring seria contornado e a
 * atomicidade (amostra + MPPTs + status do inversor) silenciosamente perdida.
 *
 * <p>Recebe o <em>id</em> do inversor (não a entidade): o objeto carregado no
 * ciclo do {@link IngestionService} pertence a uma transação já encerrada —
 * usá-lo aqui detached faria o {@code @MapsId} de {@code EnergySample} tentar
 * persistir um Inverter detached ("detached entity passed to persist").
 * Recarregar dentro da transação corrente garante instância gerenciada e
 * atualização de status por dirty checking.</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class IngestionPersister {

    private final ReadingEntityMapper mapper;
    private final EnergySampleRepository energySampleRepository;
    private final MpptReadingRepository mpptReadingRepository;
    private final InverterRepository inverterRepository;

    /** Persiste atomicamente a leitura completa e atualiza o estado do inversor. */
    @Transactional
    public void persist(EnergyReading reading, Long inverterId) {
        Inverter inverter = managed(inverterId);
        energySampleRepository.save(mapper.toSample(reading, inverter));
        mpptReadingRepository.saveAll(mapper.toMpptReadings(reading, inverter));

        inverter.setStatus(reading.status() == null ? InverterStatus.UNKNOWN : reading.status());
        inverter.setLastSeenAt(reading.sampledAt());
        // Entidade gerenciada: o UPDATE sai por dirty checking no commit.
    }

    @Transactional
    public void markOffline(Long inverterId) {
        managed(inverterId).setStatus(InverterStatus.OFFLINE);
    }

    private Inverter managed(Long inverterId) {
        return inverterRepository.findById(inverterId).orElseThrow(
                () -> new EntityNotFoundException("Inversor " + inverterId + " não existe mais"));
    }
}
