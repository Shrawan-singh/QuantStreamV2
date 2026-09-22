/*
 * ==================================================================================
 * FILE: AlertExecutionService.java
 * ==================================================================================
 *
 * WHAT THIS FILE DOES:
 * This is the "TRIPWIRE DETECTOR" for user alerts.
 *
 * HOW IT WORKS ON EVERY TICK:
 * 1. An incoming snapshot arrives for a stock (e.g. AAPL at $235.00, Score 82.5).
 * 2. This service checks in-memory cache for any active rules set by the user:
 *    - "PRICE_ABOVE 230.00" -> MET! (235.00 > 230.00)
 *    - "SCORE_ABOVE 80.0"   -> MET! (82.5 > 80.0)
 *
 * ONE-SHOT TRIGGER SEMANTICS (Crucial Concept!):
 * If an alert matched, we do NOT want to spam the user's phone with 5,000 notifications
 * every second while the price stays above $230!
 * As soon as an alert is triggered:
 *   - Its status changes to `TRIGGERED`
 *   - It is immediately disarmed: `enabled = false`
 *   - An audit trail is saved in the database
 *   - A WebSocket notification pops up on the user's screen
 * It will NEVER fire again until the user explicitly clicks "Reset / Re-arm" on the UI!
 *
 * IN-MEMORY CACHE (Performance):
 * Checking the PostgreSQL database on every single tick (1,000 times/sec) would kill
 * database performance. We keep active alerts in a fast ConcurrentHashMap (`activeAlertsCache`),
 * and only invalidate it when alerts are created or updated.
 * ==================================================================================
 */

package com.quantstream.backend.service;

import com.quantstream.backend.domain.dto.AlertTriggerNotification;
import com.quantstream.backend.domain.dto.AnalyticsSnapshot;
import com.quantstream.backend.domain.entity.AlertConfigEntity;
import com.quantstream.backend.domain.entity.AlertTriggerHistoryEntity;
import com.quantstream.backend.repository.AlertConfigRepository;
import com.quantstream.backend.repository.AlertTriggerHistoryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * High-performance alert evaluation service executed in the stream worker pipeline.
 */
@Service
public class AlertExecutionService {

    private static final Logger logger = LoggerFactory.getLogger(AlertExecutionService.class);

    private final AlertConfigRepository alertConfigRepository;
    private final AlertTriggerHistoryRepository alertTriggerHistoryRepository;
    private final SimpMessagingTemplate messagingTemplate; // WebSocket broadcaster

    // In-memory cache: Symbol -> List of armed alerts (avoids hitting database on hot path)
    private final java.util.concurrent.ConcurrentHashMap<String, java.util.List<AlertConfigEntity>> activeAlertsCache = new java.util.concurrent.ConcurrentHashMap<>();

    public AlertExecutionService(
            AlertConfigRepository alertConfigRepository,
            AlertTriggerHistoryRepository alertTriggerHistoryRepository,
            SimpMessagingTemplate messagingTemplate
    ) {
        this.alertConfigRepository = alertConfigRepository;
        this.alertTriggerHistoryRepository = alertTriggerHistoryRepository;
        this.messagingTemplate = messagingTemplate;
    }

    /**
     * Clears the in-memory cache so fresh rules are loaded from database when user modifies alerts.
     */
    public void invalidateCache(String symbol) {
        if (symbol == null || symbol.isBlank()) {
            activeAlertsCache.clear();
        } else {
            activeAlertsCache.remove(symbol.trim().toUpperCase());
        }
    }

    /**
     * Looks up active alerts for a stock from memory, or loads from DB if not cached yet.
     */
    private java.util.List<AlertConfigEntity> getActiveAlerts(String symbol) {
        return activeAlertsCache.computeIfAbsent(symbol, sym -> {
            try {
                return new java.util.concurrent.CopyOnWriteArrayList<>(
                        alertConfigRepository.findBySymbolAndEnabledTrueAndTriggeredFalse(sym)
                );
            } catch (Exception e) {
                logger.warn("Failed to load active alerts from DB for symbol {}: {}", sym, e.getMessage());
                return new java.util.concurrent.CopyOnWriteArrayList<>();
            }
        });
    }

    /**
     * Evaluates all active alerts against the latest market snapshot.
     */
    @Transactional
    public List<AlertTriggerNotification> evaluate(AnalyticsSnapshot snapshot) {
        if (snapshot == null || snapshot.symbol() == null) {
            return List.of();
        }

        String symbol = snapshot.symbol().toUpperCase();
        List<AlertConfigEntity> activeAlerts = getActiveAlerts(symbol);
        if (activeAlerts.isEmpty()) {
            return List.of(); // No active alerts for this stock
        }

        List<AlertTriggerNotification> triggeredNotifications = new ArrayList<>();
        BigDecimal price = snapshot.price();
        double score = snapshot.convictionScore();

        for (AlertConfigEntity alert : activeAlerts) {
            boolean conditionMet = false;
            BigDecimal triggerValue = null;
            String condition = alert.getConditionType().toUpperCase();

            // Evaluate threshold condition
            switch (condition) {
                case "PRICE_ABOVE" -> {
                    if (price != null && price.compareTo(alert.getThreshold()) > 0) {
                        conditionMet = true;
                        triggerValue = price;
                    }
                }
                case "PRICE_BELOW" -> {
                    if (price != null && price.compareTo(alert.getThreshold()) < 0) {
                        conditionMet = true;
                        triggerValue = price;
                    }
                }
                case "SCORE_ABOVE" -> {
                    if (snapshot.ready() && score > alert.getThreshold().doubleValue()) {
                        conditionMet = true;
                        triggerValue = BigDecimal.valueOf(score);
                    }
                }
                case "SCORE_BELOW" -> {
                    if (snapshot.ready() && score < alert.getThreshold().doubleValue()) {
                        conditionMet = true;
                        triggerValue = BigDecimal.valueOf(score);
                    }
                }
                default -> logger.warn("Unknown alert condition type: {}", condition);
            }

            // If the condition was triggered!
            if (conditionMet && triggerValue != null) {
                Instant now = Instant.now();
                synchronized (alert) {
                    // Double check nobody else triggered it first
                    if (alert.isTriggered() || !alert.isEnabled()) {
                        continue;
                    }
                    // Disarm alert (One-shot semantics: fire once and stop)
                    alert.setTriggered(true);
                    alert.setTriggeredAt(now);
                    alert.setTriggeredValue(triggerValue);
                    alert.setEnabled(false);
                }
                // Remove from in-memory cache
                activeAlerts.remove(alert);
                triggerValue = triggerValue.setScale(2, java.math.RoundingMode.HALF_UP);

                // 1. Update database record for the alert rule
                alertConfigRepository.save(alert);

                // 2. Persist audit history record
                AlertTriggerHistoryEntity history = new AlertTriggerHistoryEntity(
                        alert.getId(),
                        alert.getSymbol(),
                        alert.getConditionType(),
                        alert.getThreshold(),
                        triggerValue,
                        now
                );
                alertTriggerHistoryRepository.save(history);

                // 3. Construct real-time notification object
                String message = String.format("Alert %s %s %.2f triggered at value %.2f",
                        alert.getSymbol(), alert.getConditionType(), alert.getThreshold(), triggerValue);

                AlertTriggerNotification notification = new AlertTriggerNotification(
                        alert.getId(),
                        alert.getSymbol(),
                        alert.getConditionType(),
                        alert.getThreshold(),
                        triggerValue,
                        now,
                        "TRIGGERED",
                        message
                );
                triggeredNotifications.add(notification);

                // 4. Broadcast instant notification via WebSocket to user's screen
                if (messagingTemplate != null) {
                    try {
                        messagingTemplate.convertAndSend("/topic/alerts", notification);
                        messagingTemplate.convertAndSend("/topic/alerts/" + symbol, notification);
                    } catch (Exception e) {
                        logger.warn("Failed to broadcast alert WebSocket notification: {}", e.getMessage());
                    }
                }

                logger.info("ALERT TRIGGERED: Symbol={} Condition={} Threshold={} TriggerValue={} AlertId={}",
                        alert.getSymbol(), alert.getConditionType(), alert.getThreshold(), triggerValue, alert.getId());
            }
        }

        return triggeredNotifications;
    }

    /**
     * Re-arms a triggered or disabled alert so it can fire again.
     */
    @Transactional
    public Optional<AlertConfigEntity> resetAlert(Long alertId) {
        if (alertId == null) {
            return Optional.empty();
        }
        return alertConfigRepository.findById(Objects.requireNonNull(alertId)).map(alert -> {
            alert.setTriggered(false);
            alert.setTriggeredAt(null);
            alert.setTriggeredValue(null);
            alert.setEnabled(true);
            AlertConfigEntity saved = alertConfigRepository.save(alert);
            invalidateCache(saved.getSymbol());
            return saved;
        });
    }
}
