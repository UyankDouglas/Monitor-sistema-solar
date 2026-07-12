package com.solarmonitor.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * Pool dedicado ao agendamento da ingestão. Dois threads bastam: um ciclo em
 * execução e o próximo agendado; coletas de inversores são sequenciais por
 * desenho (o logger Solarman não lida bem com concorrência).
 */
@Configuration
public class SchedulingConfig {

    @Bean
    public TaskScheduler ingestionTaskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(2);
        scheduler.setThreadNamePrefix("ingestion-");
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        scheduler.setAwaitTerminationSeconds(10);
        return scheduler;
    }
}
