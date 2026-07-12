package com.solarmonitor.aggregation;

import com.solarmonitor.plant.repository.InverterRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.SmartLifecycle;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ScheduledFuture;

/**
 * Dispara a consolidação diária/mensal a cada 5 minutos. Mesmo padrão
 * auto-reagendável do {@code IngestionScheduler} (e mesmo pool qualificado —
 * evita a ambiguidade com o TaskScheduler interno do broker WebSocket).
 * Compartilha a flag {@code app.scheduler.enabled} para os testes desligarem
 * todo agendamento de uma vez.
 */
@Component
@Slf4j
public class AggregationScheduler implements SmartLifecycle {

    private static final Duration INTERVAL = Duration.ofMinutes(5);
    /** Primeira consolidação ~45 s após o boot: já há amostras do ciclo de 5 s. */
    private static final Duration INITIAL_DELAY = Duration.ofSeconds(45);

    private final TaskScheduler taskScheduler;
    private final AggregationService aggregationService;
    private final InverterRepository inverterRepository;
    private final boolean enabled;

    private volatile boolean running = false;
    private volatile ScheduledFuture<?> nextRun;

    public AggregationScheduler(@Qualifier("ingestionTaskScheduler") TaskScheduler taskScheduler,
                                AggregationService aggregationService,
                                InverterRepository inverterRepository,
                                @Value("${app.scheduler.enabled:true}") boolean enabled) {
        this.taskScheduler = taskScheduler;
        this.aggregationService = aggregationService;
        this.inverterRepository = inverterRepository;
        this.enabled = enabled;
    }

    @Override
    public void start() {
        if (!enabled) {
            log.info("Scheduler de agregação desabilitado (app.scheduler.enabled=false)");
            return;
        }
        running = true;
        log.info("Scheduler de agregação iniciado; consolidação a cada {} min", INTERVAL.toMinutes());
        scheduleNext(INITIAL_DELAY);
    }

    @Override
    public synchronized void stop() {
        running = false;
        ScheduledFuture<?> future = nextRun;
        if (future != null) {
            future.cancel(false);
        }
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public boolean isAutoStartup() {
        return enabled;
    }

    private synchronized void scheduleNext(Duration delay) {
        if (!running) {
            return;
        }
        nextRun = taskScheduler.schedule(this::runCycle, Instant.now().plus(delay));
    }

    private void runCycle() {
        try {
            // Loop aqui (e não no serviço): consolidateInverter precisa ser
            // invocado através do proxy Spring para abrir transação.
            for (Long inverterId : inverterRepository.findAll().stream()
                    .map(inv -> inv.getId()).toList()) {
                try {
                    aggregationService.consolidateInverter(inverterId);
                } catch (RuntimeException e) {
                    log.error("Falha consolidando inversor {}: {}", inverterId, e.getMessage(), e);
                }
            }
        } catch (RuntimeException e) {
            log.error("Consolidação falhou de forma inesperada: {}", e.getMessage(), e);
        } finally {
            scheduleNext(INTERVAL);
        }
    }
}
