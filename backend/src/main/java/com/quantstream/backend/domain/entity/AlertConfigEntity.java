/*
 * ==================================================================================
 * FILE: AlertConfigEntity.java
 * ==================================================================================
 *
 * WHAT THIS FILE DOES:
 * This represents a PRICE ALERT in the PostgreSQL database.
 *
 * A price alert is like setting an alarm: "Notify me when AAPL goes above $340."
 * Each alert has:
 *   - Which stock to watch (symbol)
 *   - What condition to check (price above/below a target, or conviction score above/below)
 *   - The target value (threshold)
 *   - Whether it's currently active, and whether it has already fired
 *
 * WHAT "Entity" MEANS:
 * An "Entity" in Java is a class that maps directly to a DATABASE TABLE.
 * Each instance of this class = one ROW in the "alert_configs" table.
 * Java annotations like @Entity, @Table, @Column tell the framework (JPA/Hibernate)
 * how to translate between Java objects and database rows automatically.
 * You never have to write raw SQL like "INSERT INTO alert_configs..." — the
 * framework does it for you when you save an AlertConfigEntity object.
 *
 * LIFECYCLE OF AN ALERT:
 *   1. User creates alert → state = "ACTIVE", triggered = false
 *   2. Price crosses threshold → state = "TRIGGERED", triggered = true, triggeredAt = now
 *   3. User can reset it → back to "ACTIVE", triggered = false
 *   4. User can disable it → state = "DISABLED", enabled = false
 * ==================================================================================
 */

package com.quantstream.backend.domain.entity;

// Jakarta Persistence annotations — these tell Java how to map this class to a database table
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

/*
 * @Entity — Tells Java: "This class represents a table in the database."
 *           Every instance of this class = one row in that table.
 *
 * @Table(name = "alert_configs") — The actual name of the database table.
 *           Without this, it would default to "alertconfigentity" which is ugly.
 */
@Entity
@Table(name = "alert_configs")
public class AlertConfigEntity {

    /*
     * @Id — This field is the PRIMARY KEY (the unique identifier for each row).
     *       Like a student's roll number — no two alerts can have the same id.
     *
     * @GeneratedValue(strategy = GenerationType.IDENTITY) — The database automatically
     *       assigns the next number (1, 2, 3...) when a new alert is created.
     *       You don't need to set the id manually.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /*
     * @Column(nullable = false, length = 20) — This field maps to a database column.
     *   - nullable = false → This field is REQUIRED (can't be empty/null)
     *   - length = 20 → Maximum 20 characters (stock tickers are short, like "AAPL")
     */
    @Column(nullable = false, length = 20)
    private String symbol;  // Which stock this alert is for, e.g., "AAPL"

    @Column(name = "condition_type", nullable = false, length = 50)
    private String conditionType; // What to check: "PRICE_ABOVE", "PRICE_BELOW", "SCORE_ABOVE", "SCORE_BELOW"

    /*
     * precision = 12 means up to 12 total digits
     * scale = 4 means 4 digits after the decimal point
     * So this can hold values like 99999999.9999 — plenty for any stock price
     */
    @Column(nullable = false, precision = 12, scale = 4)
    private BigDecimal threshold;  // The target value, e.g., 340.00

    @Column(nullable = false)
    private boolean enabled = true;  // Is this alert currently active? (user can disable it)

    @Column(nullable = false)
    private boolean triggered = false;  // Has this alert already fired? (one-shot: fires only once)

    @Column(name = "triggered_at")
    private Instant triggeredAt;  // When the alert fired (null if it hasn't fired yet)

    @Column(name = "triggered_value", precision = 12, scale = 4)
    private BigDecimal triggeredValue;  // The actual price/score that crossed the threshold

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();  // When the user created this alert

    // ===== CONSTRUCTORS =====

    /*
     * Empty constructor — required by JPA (the database framework).
     * JPA needs this to create blank objects and then fill in the fields
     * when loading data from the database.
     */
    public AlertConfigEntity() {}

    /*
     * Convenience constructor — lets us create a new alert with all the important fields.
     * The symbol is automatically converted to UPPERCASE (so "aapl" becomes "AAPL").
     */
    public AlertConfigEntity(String symbol, String conditionType, BigDecimal threshold, boolean enabled) {
        this.symbol = symbol != null ? symbol.toUpperCase() : null;
        this.conditionType = conditionType;
        this.threshold = threshold;
        this.enabled = enabled;
        this.triggered = false;
        this.createdAt = Instant.now();
    }

    // ===== GETTERS AND SETTERS =====
    // These are the standard Java way to read and write private fields.
    // Spring and JPA use these behind the scenes to access the data.
    // Each getter returns the field's value; each setter updates it.

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
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

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isTriggered() {
        return triggered;
    }

    public void setTriggered(boolean triggered) {
        this.triggered = triggered;
    }

    public Instant getTriggeredAt() {
        return triggeredAt;
    }

    public void setTriggeredAt(Instant triggeredAt) {
        this.triggeredAt = triggeredAt;
    }

    public BigDecimal getTriggeredValue() {
        return triggeredValue;
    }

    public void setTriggeredValue(BigDecimal triggeredValue) {
        this.triggeredValue = triggeredValue;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    /*
     * getState() — Returns a human-readable status string for the alert.
     * This is NOT stored in the database — it's calculated on-the-fly
     * based on the 'triggered' and 'enabled' flags.
     *
     * Logic:
     *   - If it has fired → "TRIGGERED"
     *   - If it's enabled but hasn't fired → "ACTIVE"
     *   - If the user disabled it → "DISABLED"
     */
    public String getState() {
        if (triggered) {
            return "TRIGGERED";
        }
        return enabled ? "ACTIVE" : "DISABLED";
    }
}
