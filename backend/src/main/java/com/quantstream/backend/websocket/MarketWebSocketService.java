package com.quantstream.backend.websocket;

import com.quantstream.backend.domain.dto.AnalyticsSnapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

/**
 * Service responsible for broadcasting analytics events to WebSocket clients via STOMP topics.
 */
@Service
public class MarketWebSocketService {

    private static final Logger logger = LoggerFactory.getLogger(MarketWebSocketService.class);

    private final SimpMessagingTemplate messagingTemplate;

    public MarketWebSocketService(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    /**
     * Broadcasts an analytics snapshot to both global and per-symbol topics.
     * Guaranteed non-blocking and safe from client disconnection failures.
     */
    public void broadcast(AnalyticsSnapshot snapshot) {
        if (snapshot == null || snapshot.symbol() == null) {
            return;
        }

        try {
            // Broadcast to universal market stream
            messagingTemplate.convertAndSend("/topic/market/all", snapshot);

            // Broadcast to symbol-specific stream
            messagingTemplate.convertAndSend("/topic/market/" + snapshot.symbol().toUpperCase(), snapshot);
        } catch (Exception e) {
            logger.warn("Failed to broadcast WebSocket update for symbol {}: {}", snapshot.symbol(), e.getMessage());
        }
    }
}
