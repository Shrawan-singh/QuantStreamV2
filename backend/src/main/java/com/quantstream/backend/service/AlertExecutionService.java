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
 *
 * <p>Implements strict one-shot trigger semantics: once a threshold condition is breached,
 * the alert transitions to {@code TRIGGERED}, trigger history is persisted to PostgreSQL,
 * real-time WebSocket notifications are broadcast to clients, and the alert is disarmed
 * from repeatedly triggering on subsequent ticks until explicitly re-armed.</p>
 */
@Service
public class AlertExecutionService {

    private static final Logger logger = LoggerFactory.getLogger(AlertExecutionService.class);

    private final AlertConfigRepository alertConfigRepository;
    private final AlertTriggerHistoryRepository alertTriggerHistoryRepository;
    private final SimpMessagingTemplate messagingTemplate;

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
     * Evaluates all active, un-triggered alerts for the given stock snapshot.
     * Guaranteed one-shot semantics: alerts transition to TRIGGERED and disable on match.
     *
     * @param snapshot latest analytics snapshot for an instrument
     * @return list of newly triggered alert notifications
     */
    @Transactional
    public List<AlertTriggerNotification> evaluate(AnalyticsSnapshot snapshot) {
        if (snapshot == null || snapshot.symbol() == null) {
            return List.of();
        }

        String symbol = snapshot.symbol().toUpperCase();
        List<AlertConfigEntity> activeAlerts = alertConfigRepository.findBySymbolAndEnabledTrueAndTriggeredFalse(symbol);
        if (activeAlerts.isEmpty()) {
            return List.of();
        }

        List<AlertTriggerNotification> triggeredNotifications = new ArrayList<>();
        BigDecimal price = snapshot.price();
        double score = snapshot.convictionScore();

        for (AlertConfigEntity alert : activeAlerts) {
            boolean conditionMet = false;
            BigDecimal triggerValue = null;
            String condition = alert.getConditionType().toUpperCase();

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

            if (conditionMet && triggerValue != null) {
                triggerValue = triggerValue.setScale(2, java.math.RoundingMode.HALF_UP);
                Instant now = Instant.now();

                // 1. One-shot state transition: set TRIGGERED and disarm further firing
                alert.setTriggered(true);
                alert.setTriggeredAt(now);
                alert.setTriggeredValue(triggerValue);
                alert.setEnabled(false);
                alertConfigRepository.save(alert);

                // 2. Persist trigger history record
                AlertTriggerHistoryEntity history = new AlertTriggerHistoryEntity(
                        alert.getId(),
                        alert.getSymbol(),
                        alert.getConditionType(),
                        alert.getThreshold(),
                        triggerValue,
                        now
                );
                alertTriggerHistoryRepository.save(history);

                // 3. Construct real-time notification
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

                // 4. WebSocket broadcast
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
     * Resets a triggered or disabled alert back to ACTIVE state.
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
            return alertConfigRepository.save(alert);
        });
    }
}
