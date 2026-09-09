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
@Table(name = "alert_trigger_history")
public class AlertTriggerHistoryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "alert_id", nullable = false)
    private Long alertId;

    @Column(nullable = false, length = 20)
    private String symbol;

    @Column(name = "condition_type", nullable = false, length = 50)
    private String conditionType;

    @Column(nullable = false, precision = 12, scale = 4)
    private BigDecimal threshold;

    @Column(name = "triggered_value", nullable = false, precision = 12, scale = 4)
    private BigDecimal triggeredValue;

    @Column(name = "triggered_at", nullable = false)
    private Instant triggeredAt = Instant.now();

    public AlertTriggerHistoryEntity() {}

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

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getAlertId() {
        return alertId;
    }

    public void setAlertId(Long alertId) {
        this.alertId = alertId;
    }

    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol != null ? symbol.toUpperCase() : null;
    }

    public String getConditionType() {
        return conditionType;
    }

    public void setConditionType(String conditionType) {
        this.conditionType = conditionType;
    }

    public BigDecimal getThreshold() {
        return threshold;
    }

    public void setThreshold(BigDecimal threshold) {
        this.threshold = threshold;
    }

    public BigDecimal getTriggeredValue() {
        return triggeredValue;
    }

    public void setTriggeredValue(BigDecimal triggeredValue) {
        this.triggeredValue = triggeredValue;
    }

    public Instant getTriggeredAt() {
        return triggeredAt;
    }

    public void setTriggeredAt(Instant triggeredAt) {
        this.triggeredAt = triggeredAt;
    }
}
