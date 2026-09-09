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
                @Index(name = "idx_snapshot_symbol_time", columnList = "symbol, timestamp DESC")
        }
)
public class AnalyticsSnapshotEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 20)
    private String symbol;

    @Column(nullable = false, precision = 12, scale = 4)
    private BigDecimal price;

    private Double sma;
    private Double ema;
    private Double rsi;
    private Double momentum;

    @Column(name = "relative_volume")
    private Double relativeVolume;

    @Column(name = "conviction_score")
    private Double convictionScore;

    @Column(name = "score_category", length = 30)
    private String scoreCategory;

    @Column(nullable = false)
    private Instant timestamp;

    public AnalyticsSnapshotEntity() {}

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

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public Double getSma() {
        return sma;
    }

    public void setSma(Double sma) {
        this.sma = sma;
    }

    public Double getEma() {
        return ema;
    }

    public void setEma(Double ema) {
        this.ema = ema;
    }

    public Double getRsi() {
        return rsi;
    }

    public void setRsi(Double rsi) {
        this.rsi = rsi;
    }

    public Double getMomentum() {
        return momentum;
    }

    public void setMomentum(Double momentum) {
        this.momentum = momentum;
    }

    public Double getRelativeVolume() {
        return relativeVolume;
    }

    public void setRelativeVolume(Double relativeVolume) {
        this.relativeVolume = relativeVolume;
    }

    public Double getConvictionScore() {
        return convictionScore;
    }

    public void setConvictionScore(Double convictionScore) {
        this.convictionScore = convictionScore;
    }

    public String getScoreCategory() {
        return scoreCategory;
    }

    public void setScoreCategory(String scoreCategory) {
        this.scoreCategory = scoreCategory;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }
}
