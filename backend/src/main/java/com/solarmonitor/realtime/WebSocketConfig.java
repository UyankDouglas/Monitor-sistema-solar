package com.solarmonitor.realtime;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * WebSocket com STOMP: endpoint {@code /ws}, broker simples em {@code /topic}.
 *
 * <p>Tópicos publicados:</p>
 * <ul>
 *   <li>{@code /topic/readings} — todas as leituras (dashboard geral);</li>
 *   <li>{@code /topic/inverters/{id}/readings} — leituras de um inversor.</li>
 * </ul>
 *
 * <p>Origens liberadas de forma ampla enquanto a segurança JWT não chega
 * (Etapa 6), quando o handshake passará a ser autenticado.</p>
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws").setAllowedOriginPatterns("*");
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic");
        registry.setApplicationDestinationPrefixes("/app");
    }
}
