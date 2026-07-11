package com.solarmonitor;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Ponto de entrada da aplicação de monitoramento solar Deye.
 *
 * <p>O agendamento é habilitado desde já pois o coletor de telemetria
 * (Etapa 4) roda em intervalo configurável (padrão 5s).</p>
 */
@EnableScheduling
@SpringBootApplication
public class SolarMonitorApplication {

    public static void main(String[] args) {
        SpringApplication.run(SolarMonitorApplication.class, args);
    }
}
