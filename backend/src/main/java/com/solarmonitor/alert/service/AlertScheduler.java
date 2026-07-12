package com.solarmonitor.alert.service;

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
 * Avaliação de alertas a cada 60 s — mesmo padrão auto-reagendável dos
 * demais schedulers, mesma flag {@code app.scheduler.enabled}.
 */
@Component
@Slf4j
public class AlertScheduler implements SmartLifecycle {

    private static final Duration INTERVAL = Duration.ofSeconds(60);
    private static final Duration INITIAL_DELAY = Duration.ofSeconds(20);

    private final TaskScheduler taskScheduler;
    private final AlertEngine alertEngine;
    private final InverterRepository inverterRepository;
    private final boolean enabled;

    private volatile boolean running = false;
    private volatile ScheduledFuture<?> nextRun;

    public AlertScheduler(@Qualifier("ingestionTaskScheduler") TaskScheduler taskScheduler,
                          AlertEngine alertEngine,
                          InverterRepository inverterRepository,
                          @Value("${app.scheduler.enabled:true}") boolean enabled) {
        this.taskScheduler = taskScheduler;
        this.alertEngine = alertEngine;
        this.inverterRepository = inverterRepository;
        this.enabled = enabled;
    }

    @Override
    public void start() {
        if (!enabled) {
            log.info("Scheduler de alertas desabilitado (app.scheduler.enabled=false)");
            return;
        }
        running = true;
        log.info("Motor de alertas iniciado; avaliação a cada {} s", INTERVAL.toSeconds());
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
            for (var inverter : inverterRepository.findAll()) {
                try {
                    alertEngine.evaluate(inverter.getId()); // via proxy → transação
                } catch (RuntimeException e) {
                    log.error("Avaliação de alertas falhou para o inversor {}: {}",
                            inverter.getId(), e.getMessage(), e);
                }
            }
        } catch (RuntimeException e) {
            log.error("Ciclo de alertas falhou: {}", e.getMessage(), e);
        } finally {
            scheduleNext(INTERVAL);
        }
    }
}
