package com.solarmonitor.realtime;

import com.solarmonitor.provider.EnergyReading;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

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

    /** Payload enviado aos assinantes. */
    public record ReadingEvent(Long inverterId, EnergyReading reading) {
    }
}
