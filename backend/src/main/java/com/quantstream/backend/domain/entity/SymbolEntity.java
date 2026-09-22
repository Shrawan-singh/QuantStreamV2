/*
 * ==================================================================================
 * FILE: SymbolEntity.java
 * ==================================================================================
 *
 * WHAT THIS FILE DOES:
 * This represents a STOCK SYMBOL record in the PostgreSQL "symbols" table.
 *
 * It stores basic info about each stock the system is tracking:
 *   - The ticker symbol (e.g., "AAPL")
 *   - The company name (e.g., "Apple Inc.")
 *   - Which exchange it trades on (e.g., "NASDAQ")
 *   - Whether we're currently tracking it (active = true/false)
 *
 * HOW IT'S USED:
 * When QuantStream starts up, it can register which symbols are being monitored
 * in this database table. The StockController uses this to list all tracked stocks
 * even before any price ticks have arrived.
 *
 * NOTE: The @Id here is the symbol string itself (not an auto-generated number).
 * Since stock tickers are naturally unique ("AAPL" is always Apple), we use the
 * ticker as the primary key directly.
 * ==================================================================================
 */

package com.quantstream.backend.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "symbols")
public class SymbolEntity {

    /*
     * Unlike other entities that use auto-generated numeric IDs,
     * this entity uses the stock ticker itself as the primary key.
     * "AAPL" is the ID, "TCS" is the ID, etc.
     */
    @Id
    @Column(nullable = false, length = 20)
    private String symbol;

    @Column(name = "company_name", nullable = false, length = 100)
    private String companyName;

    @Column(nullable = false, length = 20)
    private String exchange;  // Which stock exchange, e.g., "NSE", "NASDAQ", "NYSE"

    @Column(nullable = false)
    private boolean active = true;  // Is QuantStream currently monitoring this stock?

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    // Empty constructor required by JPA
    public SymbolEntity() {}

    public SymbolEntity(String symbol, String companyName, String exchange, boolean active) {
        this.symbol = symbol != null ? symbol.toUpperCase() : null;
        this.companyName = companyName;
        this.exchange = exchange;
        this.active = active;
        this.createdAt = Instant.now();
    }

    // ===== GETTERS AND SETTERS =====

    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol != null ? symbol.toUpperCase() : null; }

    public String getCompanyName() { return companyName; }
    public void setCompanyName(String companyName) { this.companyName = companyName; }

    public String getExchange() { return exchange; }
    public void setExchange(String exchange) { this.exchange = exchange; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
