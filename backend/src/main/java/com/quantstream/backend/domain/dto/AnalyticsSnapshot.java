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
        Instant timestamp,
        double rawConvictionScore,
        double realizedVolatility
) {
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
     * Backwards-compatible 25-argument constructor.
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
                convictionScore,
                0.0
        );
    }
}
