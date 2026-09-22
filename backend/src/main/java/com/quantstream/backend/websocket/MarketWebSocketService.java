/*
 * ==================================================================================
 * FILE: MarketWebSocketService.java
 * ==================================================================================
 *
 * WHAT THIS FILE DOES:
 * This is the "BROADCAST TOWER" sending real-time data to user web browsers.
 *
 * HOW THE FRONTEND RECEIVES LIVE UPDATES WITHOUT REFRESHING:
 * 1. The browser connects via WebSocket to the backend using the STOMP protocol.
 * 2. It subscribes to "topics" (like radio channels):
 *    - `/topic/market/all`: Listens to updates for EVERY stock (used by the main dashboard).
 *    - `/topic/market/AAPL`: Listens ONLY to Apple stock updates (used by a dedicated detail chart).
 * 3. Whenever a new tick is processed, this service calls `messagingTemplate.convertAndSend(...)`.
 *    The JSON payload instantly travels down the open WebSocket connection, and the React UI
 *    re-renders the price flash (green/red) and conviction gauge without a page reload!
 * ==================================================================================
 */

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

    // Spring helper for broadcasting STOMP messages over WebSockets
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
            // Channel 1: Universal market stream (used by the main stock table)
            messagingTemplate.convertAndSend("/topic/market/all", snapshot);

            // Channel 2: Symbol-specific stream (e.g. "/topic/market/AAPL", used by specific stock detail modals)
            messagingTemplate.convertAndSend("/topic/market/" + snapshot.symbol().toUpperCase(), snapshot);
        } catch (Exception e) {
            logger.warn("Failed to broadcast WebSocket update for symbol {}: {}", snapshot.symbol(), e.getMessage());
        }
    }
}
