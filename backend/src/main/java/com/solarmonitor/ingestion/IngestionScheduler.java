package com.solarmonitor.ingestion;

import com.solarmonitor.config.service.ConfigurationService;
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
 * Agendador da coleta com intervalo reconfigurável em runtime: em vez de
 * {@code @Scheduled(fixedRate)} (intervalo fixo na inicialização), cada ciclo
 * agenda o próximo lendo {@code scheduler.reading-interval-ms} do banco —
 * mudanças pela tela de Configurações valem no ciclo seguinte, sem restart.
 *
 * <p>{@link SmartLifecycle} garante início após o contexto completo (Flyway
 * incluso) e parada limpa no shutdown. Desabilitável por
 * {@code app.scheduler.enabled=false} (usado nos testes).</p>
 */
@Component
@Slf4j
public class IngestionScheduler implements SmartLifecycle {

    private final TaskScheduler taskScheduler;
    private final IngestionService ingestionService;
    private final ConfigurationService configurations;
    private final boolean enabled;

    private volatile boolean running = false;
    private volatile ScheduledFuture<?> nextRun;
    /** Último intervalo lido com sucesso — fallback se o banco estiver fora. */
    private volatile long lastKnownIntervalMs = 5_000L;

    public IngestionScheduler(@Qualifier("ingestionTaskScheduler") TaskScheduler taskScheduler,
                              IngestionService ingestionService,
                              ConfigurationService configurations,
                              @Value("${app.scheduler.enabled:true}") boolean enabled) {
        this.taskScheduler = taskScheduler;
        this.ingestionService = ingestionService;
        this.configurations = configurations;
        this.enabled = enabled;
    }

    @Override
    public void start() {
        if (!enabled) {
            log.info("Scheduler de ingestão desabilitado (app.scheduler.enabled=false)");
            return;
        }
        running = true;
        log.info("Scheduler de ingestão iniciado; intervalo atual {} ms", safeIntervalMs());
        scheduleNext(Duration.ofMillis(1_000)); // primeira coleta ~1 s após o boot
    }

    /** Sincronizado com {@link #scheduleNext} para o par (running=false, cancel) ser atômico. */
    @Override
    public synchronized void stop() {
        running = false;
        ScheduledFuture<?> future = nextRun;
        if (future != null) {
            future.cancel(false);
        }
        log.info("Scheduler de ingestão parado");
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public boolean isAutoStartup() {
        return enabled;
    }

    /**
     * Sincronizado com {@link #stop}: sem isso, um ciclo em voo poderia ler
     * {@code running=true}, o stop cancelar o future antigo, e o ciclo agendar
     * um novo future órfão que executaria durante o shutdown do contexto.
     */
    private synchronized void scheduleNext(Duration delay) {
        if (!running) {
            return;
        }
        nextRun = taskScheduler.schedule(this::runCycle, Instant.now().plus(delay));
    }

    private void runCycle() {
        try {
            ingestionService.ingestAll();
        } catch (RuntimeException e) {
            log.error("Ciclo de ingestão falhou de forma inesperada: {}", e.getMessage(), e);
        } finally {
            // safeIntervalMs nunca lança: o reagendamento não pode depender
            // do banco estar de pé — senão uma queda do PostgreSQL durante um
            // ciclo mataria o scheduler silenciosamente até o restart.
            scheduleNext(Duration.ofMillis(safeIntervalMs()));
        }
    }

    /** Intervalo do banco; em falha de acesso, usa o último valor conhecido. */
    private long safeIntervalMs() {
        try {
            long interval = configurations.getReadingIntervalMs();
            lastKnownIntervalMs = interval;
            return interval;
        } catch (RuntimeException e) {
            log.warn("Falha ao ler intervalo de leitura do banco ({}); usando último conhecido {} ms",
                    e.getMessage(), lastKnownIntervalMs);
            return lastKnownIntervalMs;
        }
    }
}
