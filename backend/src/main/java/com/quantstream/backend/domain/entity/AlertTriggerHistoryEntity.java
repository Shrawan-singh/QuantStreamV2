/*
 * ==================================================================================
 * FILE: AlertTriggerHistoryEntity.java
 * ==================================================================================
 *
 * WHAT THIS FILE DOES:
 * This is a HISTORY LOG that records every time an alert fires.
 *
 * While AlertConfigEntity tracks the alert's current state (is it active? has it
 * triggered?), this entity creates a permanent AUDIT TRAIL — a record of exactly
 * when each alert fired, what the threshold was, and what the actual value was.
 *
 * ANALOGY:
 * AlertConfigEntity is like a fire alarm on the wall — it shows if it's armed or tripped.
 * AlertTriggerHistoryEntity is the fire department's log book — it records every time
 * the alarm went off: date, time, which alarm, what triggered it.
 *
 * WHY KEEP HISTORY:
 * If a user resets and re-arms an alert, the AlertConfigEntity goes back to "ACTIVE",
 * losing the information about when it previously fired. This history table preserves
 * that information permanently.
 * ==================================================================================
 */

package com.quantstream.backend.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "alert_trigger_history")  // Maps to the "alert_trigger_history" table in PostgreSQL
public class AlertTriggerHistoryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;  // Unique ID for this history entry

    @Column(name = "alert_id", nullable = false)
    private Long alertId;  // Which alert (from alert_configs table) was triggered

    @Column(nullable = false, length = 20)
    private String symbol;  // Which stock, e.g., "AAPL"

    @Column(name = "condition_type", nullable = false, length = 50)
    private String conditionType;  // What condition was checked, e.g., "PRICE_ABOVE"

    @Column(nullable = false, precision = 12, scale = 4)
    private BigDecimal threshold;  // What target value the user set

    @Column(name = "triggered_value", nullable = false, precision = 12, scale = 4)
    private BigDecimal triggeredValue;  // What the actual price/score was when it triggered

    @Column(name = "triggered_at", nullable = false)
    private Instant triggeredAt = Instant.now();  // Exact moment the alert fired

    // Empty constructor required by JPA (database framework needs this)
    public AlertTriggerHistoryEntity() {}

    // Full constructor to create a new history record when an alert fires
    public AlertTriggerHistoryEntity(
            Long alertId,
            String symbol,
            String conditionType,
            BigDecimal threshold,
            BigDecimal triggeredValue,
            Instant triggeredAt
    ) {
        this.alertId = alertId;
        this.symbol = symbol != null ? symbol.toUpperCase() : null;
        this.conditionType = conditionType;
        this.threshold = threshold;
        this.triggeredValue = triggeredValue;
        this.triggeredAt = triggeredAt != null ? triggeredAt : Instant.now();
    }

    // ===== GETTERS AND SETTERS (standard Java boilerplate for reading/writing fields) =====

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getAlertId() { return alertId; }
    public void setAlertId(Long alertId) { this.alertId = alertId; }

    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol != null ? symbol.toUpperCase() : null; }

    public String getConditionType() { return conditionType; }
    public void setConditionType(String conditionType) { this.conditionType = conditionType; }

    public BigDecimal getThreshold() { return threshold; }
    public void setThreshold(BigDecimal threshold) { this.threshold = threshold; }

    public BigDecimal getTriggeredValue() { return triggeredValue; }
    public void setTriggeredValue(BigDecimal triggeredValue) { this.triggeredValue = triggeredValue; }

    public Instant getTriggeredAt() { return triggeredAt; }
    public void setTriggeredAt(Instant triggeredAt) { this.triggeredAt = triggeredAt; }
}
