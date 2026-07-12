package com.solarmonitor.weather.service;

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
 * Atualização de clima a cada hora (a Open-Meteo atualiza os modelos em
 * ciclos horários — mais frequente só desperdiçaria cota de cortesia).
 * Mesmo padrão auto-reagendável e mesma flag dos demais schedulers.
 */
@Component
@Slf4j
public class WeatherScheduler implements SmartLifecycle {

    private static final Duration INTERVAL = Duration.ofHours(1);
    private static final Duration INITIAL_DELAY = Duration.ofSeconds(90);

    private final TaskScheduler taskScheduler;
    private final WeatherService weatherService;
    private final boolean enabled;

    private volatile boolean running = false;
    private volatile ScheduledFuture<?> nextRun;

    public WeatherScheduler(@Qualifier("ingestionTaskScheduler") TaskScheduler taskScheduler,
                            WeatherService weatherService,
                            @Value("${app.scheduler.enabled:true}") boolean enabled) {
        this.taskScheduler = taskScheduler;
        this.weatherService = weatherService;
        this.enabled = enabled;
    }

    @Override
    public void start() {
        if (!enabled) {
            log.info("Scheduler de clima desabilitado (app.scheduler.enabled=false)");
            return;
        }
        running = true;
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
            weatherService.refresh();
        } catch (RuntimeException e) {
            log.warn("Atualização de clima falhou: {}", e.getMessage());
        } finally {
            scheduleNext(INTERVAL);
        }
    }
}
