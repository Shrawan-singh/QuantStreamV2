package com.quantstream.backend.analytics.scoring;

import com.quantstream.backend.analytics.indicator.IndicatorResult;
import com.quantstream.backend.analytics.indicator.Signal;
import com.quantstream.backend.analytics.state.MarketState;
import com.quantstream.backend.config.ScoringProperties;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Deterministic, explainable scoring engine that combines quantitative technical
 * indicators into a unified Conviction Score on a 0–100 scale.
 *
 * <p>Weights are fully configurable. Indicators contribute proportionally based on
 * their evaluated {@link Signal} (POSITIVE = 1.0, NEUTRAL = 0.5, NEGATIVE = 0.0, NOT_READY = 0.5).</p>
 */
@Component
public class ConvictionScoreEngine {

    private final ScoringProperties properties;

    public ConvictionScoreEngine(ScoringProperties properties) {
        this.properties = properties != null ? properties : ScoringProperties.defaultProperties();
    }

    /**
     * Evaluates conviction score given market state snapshot and computed indicator results.
     */
    public ConvictionScore evaluate(
            MarketState.Snapshot snapshot,
            IndicatorResult smaResult,
            IndicatorResult emaResult,
            IndicatorResult rsiResult,
            IndicatorResult momentumResult,
            IndicatorResult rvolResult
    ) {
        String symbol = snapshot != null ? snapshot.symbol() : "UNKNOWN";
        Instant timestamp = snapshot != null && snapshot.latestTimestamp() != null
                ? snapshot.latestTimestamp()
                : Instant.now();

        boolean anyReady = (smaResult != null && smaResult.ready()) ||
                (emaResult != null && emaResult.ready()) ||
                (rsiResult != null && rsiResult.ready()) ||
                (momentumResult != null && momentumResult.ready()) ||
                (rvolResult != null && rvolResult.ready());

        if (!anyReady) {
            return ConvictionScore.notReady(symbol, timestamp);
        }

        double totalWeight = properties.totalWeight();
        if (totalWeight <= 0.0) {
            totalWeight = 100.0;
        }

        // 1. Trend component (evaluates primarily SMA, supported by EMA if available)
        Signal trendSignal = (smaResult != null && smaResult.ready())
                ? smaResult.signal()
                : ((emaResult != null && emaResult.ready()) ? emaResult.signal() : Signal.NOT_READY);
        double trendRatio = signalToRatio(trendSignal);
        double trendScore = (properties.trendWeight() / totalWeight) * 100.0 * trendRatio;

        // 2. Momentum component
        Signal momentumSignal = (momentumResult != null && momentumResult.ready())
                ? momentumResult.signal()
                : Signal.NOT_READY;
        double momentumRatio = signalToRatio(momentumSignal);
        double momentumScore = (properties.momentumWeight() / totalWeight) * 100.0 * momentumRatio;

        // 3. RSI component
        Signal rsiSignal = (rsiResult != null && rsiResult.ready())
                ? rsiResult.signal()
                : Signal.NOT_READY;
        double rsiRatio = signalToRatio(rsiSignal);
        double rsiScore = (properties.rsiWeight() / totalWeight) * 100.0 * rsiRatio;

        // 4. Volume component
        Signal volumeSignal = (rvolResult != null && rvolResult.ready())
                ? rvolResult.signal()
                : Signal.NOT_READY;
        double volumeRatio = signalToRatio(volumeSignal);
        double volumeScore = (properties.volumeWeight() / totalWeight) * 100.0 * volumeRatio;

        double compositeScore = clamp(trendScore + momentumScore + rsiScore + volumeScore, 0.0, 100.0);
        compositeScore = roundOneDecimal(compositeScore);

        ScoreCategory category = ScoreCategory.fromScore(compositeScore);

        // Build explanations
        List<String> explanations = new ArrayList<>(4);
        if (smaResult != null && smaResult.ready() && snapshot != null) {
            explanations.add(String.format("Trend: %s (Price ₹%s vs SMA ₹%.2f)",
                    trendSignal, snapshot.latestPrice(), smaResult.value()));
        } else {
            explanations.add("Trend: Neutral (Awaiting sufficient history for SMA)");
        }

        if (momentumResult != null && momentumResult.ready()) {
            explanations.add(String.format("Momentum: %s (Lookback change: %+.2f%%)",
                    momentumSignal, momentumResult.value()));
        } else {
            explanations.add("Momentum: Neutral (Awaiting lookback period)");
        }

        if (rsiResult != null && rsiResult.ready()) {
            explanations.add(String.format("RSI: %s (14-period index: %.1f)",
                    rsiSignal, rsiResult.value()));
        } else {
            explanations.add("RSI: Neutral (Awaiting 14-period warm-up)");
        }

        if (rvolResult != null && rvolResult.ready()) {
            String volLabel = volumeSignal == Signal.POSITIVE ? "Elevated" : (volumeSignal == Signal.NEGATIVE ? "Subdued" : "Typical");
            explanations.add(String.format("Volume: %s (%.2fx of 20-period baseline)",
                    volLabel, rvolResult.value()));
        } else {
            explanations.add("Volume: Baseline (Awaiting volume history)");
        }

        Map<String, Signal> signals = new HashMap<>();
        signals.put("trend", trendSignal);
        signals.put("momentum", momentumSignal);
        signals.put("rsi", rsiSignal);
        signals.put("volume", volumeSignal);

        return new ConvictionScore(
                symbol,
                compositeScore,
                category,
                roundOneDecimal(trendScore),
                roundOneDecimal(momentumScore),
                roundOneDecimal(rsiScore),
                roundOneDecimal(volumeScore),
                explanations,
                signals,
                true,
                timestamp
        );
    }

    private double signalToRatio(Signal signal) {
        if (signal == null) return 0.5;
        return switch (signal) {
            case POSITIVE -> 1.0;
            case NEGATIVE -> 0.0;
            case NEUTRAL, NOT_READY -> 0.5;
        };
    }

    private double clamp(double val, double min, double max) {
        return Math.max(min, Math.min(max, val));
    }

    private double roundOneDecimal(double val) {
        return BigDecimal.valueOf(val).setScale(1, RoundingMode.HALF_UP).doubleValue();
    }
}
