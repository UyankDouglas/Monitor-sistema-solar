package com.solarmonitor.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * Pool dedicado aos agendamentos da aplicação (ingestão + agregação). Três
 * threads: ciclo de ingestão em execução, consolidação eventual e o próximo
 * agendamento; coletas de inversores são sequenciais por desenho (o logger
 * Solarman não lida bem com concorrência).
 */
@Configuration
public class SchedulingConfig {

    @Bean
    public TaskScheduler ingestionTaskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(3);
        scheduler.setThreadNamePrefix("ingestion-");
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        scheduler.setAwaitTerminationSeconds(10);
        return scheduler;
    }
}
