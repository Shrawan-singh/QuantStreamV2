/*
 * ==================================================================================
 * FILE: AnalyticsSnapshot.java
 * ==================================================================================
 *
 * WHAT THIS FILE DOES:
 * This is the MOST IMPORTANT data object in the entire project. It's the complete
 * "report card" for a single stock at a specific moment in time.
 *
 * Every time a new stock tick comes in, the analytics engine recalculates everything
 * and packages it into THIS object. It then gets:
 *   1. Sent to the frontend via WebSocket (for real-time dashboard updates)
 *   2. Returned by the REST API when the frontend asks "what's AAPL's status?"
 *   3. Saved to the PostgreSQL database for historical records
 *
 * WHAT'S INSIDE:
 * - Basic price info (current price, open, high, low, volume)
 * - Technical indicators (SMA, EMA, RSI, Momentum) — math formulas that analyze price trends
 * - Conviction Score (0-100) — our custom "is this stock looking good?" score
 * - Score breakdown — which factors contributed how much to the final score
 * - Human-readable explanations — plain English reasons for the score
 *
 * WHAT "DTO" MEANS:
 * DTO = Data Transfer Object. It's just a container for carrying data from one
 * place to another. Think of it like an envelope — it doesn't DO anything,
 * it just holds the information that needs to be delivered.
 * ==================================================================================
 */

package com.quantstream.backend.domain.dto;

import com.quantstream.backend.analytics.indicator.Signal;
import com.quantstream.backend.analytics.scoring.ScoreCategory;
import com.quantstream.backend.domain.TickSource;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Unified real-time DTO carrying price action, technical indicators, and explainable conviction score.
 * Delivered to frontend clients via WebSocket and REST APIs.
 */
public record AnalyticsSnapshot(
        // ===== BASIC STOCK INFO =====
        String symbol,               // Stock ticker, e.g., "AAPL"
        String companyName,          // Full company name, e.g., "Apple Inc."

        // ===== PRICE DATA =====
        BigDecimal price,            // Current/latest price
        BigDecimal previousPrice,    // Price from the previous tick (used to show if it went up or down)
        BigDecimal priceChange,      // How much the price changed in dollars/rupees
        double priceChangePercent,   // How much the price changed as a percentage
        BigDecimal openPrice,        // Price at market open (first tick of the day)
        BigDecimal highPrice,        // Highest price seen today
        BigDecimal lowPrice,         // Lowest price seen today

        // ===== VOLUME DATA =====
        long volume,                 // Number of shares traded in the latest tick
        long cumulativeVolume,       // Total shares traded across ALL ticks so far

        // ===== TECHNICAL INDICATORS (the "math analysis" of the stock) =====
        double sma,                  // Simple Moving Average — average price over last 20 ticks (smooths out noise)
        double ema,                  // Exponential Moving Average — like SMA but gives more weight to recent prices
        double rsi,                  // Relative Strength Index (0-100) — measures if stock is overbought (>70) or oversold (<30)
        double momentum,             // How fast the price is changing — positive means going up, negative means going down
        double relativeVolume,       // Current volume compared to average volume (>1 means unusually high trading activity)

        // ===== CONVICTION SCORE (our custom "stock health" score) =====
        double convictionScore,      // The final smoothed score (0-100): <30 = Weak, 30-50 = Moderate, 50-70 = Neutral, 70+ = Strong
        ScoreCategory scoreCategory, // Label like "STRONG", "NEUTRAL", "WEAK", "MODERATE"

        // ===== SCORE BREAKDOWN (explains HOW we calculated the score) =====
        Map<String, Double> factorScores,    // Individual factor scores: {"trend": 94.9, "volume": 50.0, "rsi": 75.8, "momentum": 100.0}
        Map<String, Double> scoreBreakdown,  // How much each factor contributed: {"trend": 23.7, "volume": 12.5, "rsi": 19.0, "momentum": 25.0}
        Map<String, Signal> signals,         // Signal for each factor: {"trend": "POSITIVE", "volume": "NEUTRAL", ...}
        List<String> explanations,           // Human-readable explanations: ["Trend: 94.9 (Price above SMA)", ...]

        // ===== META INFO =====
        boolean ready,               // Whether enough data has been collected for reliable analysis (need ~20 ticks to warm up)
        TickSource source,           // Where data comes from: SIMULATION or LIVE_PROVIDER
        Instant timestamp,           // When this snapshot was created
        double rawConvictionScore,   // The unsmoothed conviction score (before applying the smoothing filter)
        double realizedVolatility    // How much the price has been bouncing around (higher = more volatile)
) {
    /*
     * "Compact constructor" — this special block runs automatically when creating
     * a new AnalyticsSnapshot. It makes all the Maps and Lists "unmodifiable",
     * meaning nobody can accidentally change the data after the snapshot is created.
     * This is a safety measure — once a snapshot is created, it's frozen forever.
     */
    public AnalyticsSnapshot {
        if (factorScores != null) {
            factorScores = Collections.unmodifiableMap(factorScores);
        }
        if (scoreBreakdown != null) {
            scoreBreakdown = Collections.unmodifiableMap(scoreBreakdown);
        }
        if (signals != null) {
            signals = Collections.unmodifiableMap(signals);
        }
        if (explanations != null) {
            explanations = Collections.unmodifiableList(explanations);
        }
    }

    /**
     * Backwards-compatible constructor that doesn't require rawConvictionScore
     * and realizedVolatility. This exists so older code that doesn't know about
     * these new fields can still create snapshots without breaking.
     * It fills in default values: rawConvictionScore = convictionScore, realizedVolatility = 0.0
     */
    public AnalyticsSnapshot(
            String symbol,
            String companyName,
            BigDecimal price,
            BigDecimal previousPrice,
            BigDecimal priceChange,
            double priceChangePercent,
            BigDecimal openPrice,
            BigDecimal highPrice,
            BigDecimal lowPrice,
            long volume,
            long cumulativeVolume,
            double sma,
            double ema,
            double rsi,
            double momentum,
            double relativeVolume,
            double convictionScore,
            ScoreCategory scoreCategory,
            Map<String, Double> factorScores,
            Map<String, Double> scoreBreakdown,
            Map<String, Signal> signals,
            List<String> explanations,
            boolean ready,
            TickSource source,
            Instant timestamp
    ) {
        this(
                symbol,
                companyName,
                price,
                previousPrice,
                priceChange,
                priceChangePercent,
                openPrice,
                highPrice,
                lowPrice,
                volume,
                cumulativeVolume,
                sma,
                ema,
                rsi,
                momentum,
                relativeVolume,
                convictionScore,
                scoreCategory,
                factorScores,
                scoreBreakdown,
                signals,
                explanations,
                ready,
                source,
                timestamp,
                convictionScore,  // rawConvictionScore defaults to same as smoothed score
                0.0               // realizedVolatility defaults to 0
        );
    }
}
