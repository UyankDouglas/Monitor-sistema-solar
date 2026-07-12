package com.solarmonitor.realtime;

import com.solarmonitor.alert.domain.Alert;
import com.solarmonitor.provider.EnergyReading;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Publica leituras nos tópicos STOMP. Erros de broadcast são engolidos com
 * log: tempo real é conveniência — a persistência já aconteceu e o histórico
 * está íntegro.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RealtimePublisher {

    public static final String TOPIC_ALL = "/topic/readings";
    public static final String TOPIC_INVERTER = "/topic/inverters/%d/readings";
    public static final String TOPIC_ALERTS = "/topic/alerts";

    private final SimpMessagingTemplate messagingTemplate;

    public void publishReading(Long inverterId, EnergyReading reading) {
        ReadingEvent event = new ReadingEvent(inverterId, reading);
        try {
            messagingTemplate.convertAndSend(TOPIC_ALL, event);
            messagingTemplate.convertAndSend(TOPIC_INVERTER.formatted(inverterId), event);
        } catch (RuntimeException e) {
            log.warn("Falha ao publicar leitura do inversor {} no WebSocket: {}", inverterId, e.getMessage());
        }
    }

    /** Disparo/resolução de alerta → UI atualiza badge e mostra notificação. */
    public void publishAlert(String action, Alert alert) {
        AlertEvent event = new AlertEvent(action, alert.getId(), alert.getInverter().getId(),
                alert.getType().name(), alert.getSeverity().name(), alert.getStatus().name(),
                alert.getMessage(), alert.getTriggeredAt());
        try {
            messagingTemplate.convertAndSend(TOPIC_ALERTS, event);
        } catch (RuntimeException e) {
            log.warn("Falha ao publicar alerta {} no WebSocket: {}", alert.getId(), e.getMessage());
        }
    }

    /** Payload enviado aos assinantes. */
    public record ReadingEvent(Long inverterId, EnergyReading reading) {
    }

    public record AlertEvent(String action, Long alertId, Long inverterId, String type,
                             String severity, String status, String message, Instant triggeredAt) {
    }
}
