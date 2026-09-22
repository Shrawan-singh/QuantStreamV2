/*
 * ==================================================================================
 * FILE: AnalyticsSnapshotEntity.java
 * ==================================================================================
 *
 * WHAT THIS FILE DOES:
 * This saves a HISTORICAL RECORD of a stock's analytics to the PostgreSQL database.
 *
 * Every time we calculate new analytics for a stock (price, SMA, RSI, conviction
 * score, etc.), we can optionally save a snapshot to this table. This creates a
 * time-series history that could be used for:
 *   - Historical charts ("What was AAPL's conviction score yesterday?")
 *   - Backtesting ("How well did our scoring model predict stock movements?")
 *   - Data export and analysis
 *
 * NOTE: This entity stores a SUBSET of the full AnalyticsSnapshot DTO.
 * The DTO has ~25 fields (all the real-time data), but we only persist the
 * most important ones to the database to save storage space.
 *
 * DATABASE INDEX:
 * The @Index annotation creates a database index on (symbol, timestamp DESC).
 * An index is like the index at the back of a book — it makes lookups faster.
 * Without it, finding "all snapshots for AAPL" would require scanning every
 * row in the table. With the index, it jumps straight to the AAPL rows.
 * ==================================================================================
 */

package com.quantstream.backend.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(
        name = "analytics_snapshots",
        indexes = {
                // This index speeds up queries like "get latest snapshots for AAPL ordered by time"
                @Index(name = "idx_snapshot_symbol_time", columnList = "symbol, timestamp DESC")
        }
)
public class AnalyticsSnapshotEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 20)
    private String symbol;  // Stock ticker, e.g., "AAPL"

    @Column(nullable = false, precision = 12, scale = 4)
    private BigDecimal price;  // The stock price at this moment

    // ===== TECHNICAL INDICATORS (saved for historical analysis) =====
    private Double sma;             // Simple Moving Average (average of last 20 prices)
    private Double ema;             // Exponential Moving Average (weighted average favoring recent prices)
    private Double rsi;             // Relative Strength Index (0-100, measures overbought/oversold)
    private Double momentum;        // Rate of price change

    @Column(name = "relative_volume")
    private Double relativeVolume;  // Current volume vs average volume ratio

    @Column(name = "conviction_score")
    private Double convictionScore; // Our custom scoring model output (0-100)

    @Column(name = "score_category", length = 30)
    private String scoreCategory;   // "STRONG", "NEUTRAL", "WEAK", "MODERATE"

    @Column(nullable = false)
    private Instant timestamp;  // When this snapshot was recorded

    // Empty constructor required by JPA
    public AnalyticsSnapshotEntity() {}

    // Full constructor for creating a new snapshot to save
    public AnalyticsSnapshotEntity(
            String symbol,
            BigDecimal price,
            Double sma,
            Double ema,
            Double rsi,
            Double momentum,
            Double relativeVolume,
            Double convictionScore,
            String scoreCategory,
            Instant timestamp
    ) {
        this.symbol = symbol != null ? symbol.toUpperCase() : null;
        this.price = price;
        this.sma = sma;
        this.ema = ema;
        this.rsi = rsi;
        this.momentum = momentum;
        this.relativeVolume = relativeVolume;
        this.convictionScore = convictionScore;
        this.scoreCategory = scoreCategory;
        this.timestamp = timestamp != null ? timestamp : Instant.now();
    }

    // ===== GETTERS AND SETTERS =====

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol != null ? symbol.toUpperCase() : null; }

    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }

    public Double getSma() { return sma; }
    public void setSma(Double sma) { this.sma = sma; }

    public Double getEma() { return ema; }
    public void setEma(Double ema) { this.ema = ema; }

    public Double getRsi() { return rsi; }
    public void setRsi(Double rsi) { this.rsi = rsi; }

    public Double getMomentum() { return momentum; }
    public void setMomentum(Double momentum) { this.momentum = momentum; }

    public Double getRelativeVolume() { return relativeVolume; }
    public void setRelativeVolume(Double relativeVolume) { this.relativeVolume = relativeVolume; }

    public Double getConvictionScore() { return convictionScore; }
    public void setConvictionScore(Double convictionScore) { this.convictionScore = convictionScore; }

    public String getScoreCategory() { return scoreCategory; }
    public void setScoreCategory(String scoreCategory) { this.scoreCategory = scoreCategory; }

    public Instant getTimestamp() { return timestamp; }
    public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }
}
