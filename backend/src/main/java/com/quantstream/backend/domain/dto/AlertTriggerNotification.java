package com.quantstream.backend.domain.dto;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Real-time event payload broadcast to STOMP topic `/topic/alerts` upon threshold breach.
 */
public record AlertTriggerNotification(
        Long alertId,
        String symbol,
        String conditionType,
        BigDecimal threshold,
        BigDecimal triggeredValue,
        Instant triggeredAt,
        String state,
        String message
) {}
