/*
 * ==================================================================================
 * FILE: WebSocketConfig.java
 * ==================================================================================
 *
 * WHAT THIS FILE DOES:
 * Configures the Spring WebSocket Message Broker using STOMP.
 *
 * HOW REAL-TIME WEB STREAMING IS SET UP:
 * 1. Endpoint: `/ws`
 *    The browser opens a WebSocket handshake to `http://localhost:8080/ws`.
 *    "SockJS" fallback ensures older browsers or proxies can still connect smoothly.
 *
 * 2. Message Broker Prefix: `/topic`
 *    Enables a simple in-memory message broker that delivers broadcast updates to
 *    subscribed clients (e.g. `/topic/market/all`, `/topic/alerts`).
 *
 * 3. Client Send Prefix: `/app`
 *    Any messages sent FROM the browser back to the server are routed to methods
 *    annotated with `@MessageMapping`.
 * ==================================================================================
 */

package com.quantstream.backend.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * Spring WebSocket message broker configuration using STOMP over SockJS.
 *
 * <p>Enables broadcasting real-time market updates to connected browser clients.</p>
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(@NonNull MessageBrokerRegistry registry) {
        // In-memory message broker prefix for client subscription channels (e.g. /topic/market/all)
        registry.enableSimpleBroker("/topic");
        // Prefix for messages sent from browser clients to the backend server
        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(@NonNull StompEndpointRegistry registry) {
        // The URL endpoint that browsers connect to for WebSockets
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*")
                .withSockJS(); // Fallback transport if direct WebSockets are blocked by a corporate firewall
    }
}
