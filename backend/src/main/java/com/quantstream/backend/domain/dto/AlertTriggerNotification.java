/*
 * ==================================================================================
 * FILE: AlertTriggerNotification.java
 * ==================================================================================
 *
 * WHAT THIS FILE DOES:
 * When a price alert gets triggered (for example, "tell me when AAPL goes above $340"),
 * this is the notification message that gets sent to the user's browser in real-time.
 *
 * It's a DTO (Data Transfer Object) — a package of data that travels from the
 * backend server to the frontend browser via WebSocket.
 *
 * HOW IT WORKS:
 * 1. User creates an alert: "Notify me when AAPL price goes above $340"
 * 2. Live ticks keep coming in: AAPL is $338, $339, $340.50...
 * 3. The AlertExecutionService sees AAPL crossed $340 → TRIGGERED!
 * 4. It creates THIS notification object and broadcasts it to the frontend
 *    via the WebSocket channel "/topic/alerts"
 * 5. The user's browser receives it instantly and shows a toast/popup
 *
 * WHAT "STOMP" MEANS:
 * STOMP is a messaging protocol (like a walkie-talkie channel). The frontend
 * "subscribes" to "/topic/alerts" and receives notifications the instant they happen,
 * without needing to constantly ask "any alerts yet? any alerts yet?"
 * ==================================================================================
 */

package com.quantstream.backend.domain.dto;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Real-time event payload broadcast to STOMP topic `/topic/alerts` upon threshold breach.
 */
public record AlertTriggerNotification(
        Long alertId,            // The database ID of the alert that was triggered
        String symbol,           // Which stock triggered it, e.g., "AAPL"
        String conditionType,    // What kind of alert: "PRICE_ABOVE", "PRICE_BELOW", "SCORE_ABOVE", "SCORE_BELOW"
        BigDecimal threshold,    // The target value the user set, e.g., 340.00
        BigDecimal triggeredValue, // The actual value that crossed the threshold, e.g., 340.50
        Instant triggeredAt,     // When exactly it triggered
        String state,            // Current state of the alert: "TRIGGERED"
        String message           // Human-readable message, e.g., "AAPL crossed above $340.00 (now $340.50)"
) {}
