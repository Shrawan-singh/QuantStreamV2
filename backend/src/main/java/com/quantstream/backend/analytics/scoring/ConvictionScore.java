package com.quantstream.backend.analytics.scoring;

import com.quantstream.backend.analytics.indicator.Signal;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Immutable result of the continuous Conviction Score evaluation.
 *
 * @param symbol               stock ticker symbol
 * @param score                composite displayed score between 0.0 and 100.0 (smoothed, rounded to 1 decimal place)
 * @param category             qualitative category (VERY_WEAK, WEAK, NEUTRAL, STRONG, VERY_STRONG)
 * @param trendScore           continuous factor score for trend (0.0 - 100.0)
 * @param momentumScore        continuous factor score for momentum (0.0 - 100.0)
 * @param rsiScore             continuous factor score for RSI (0.0 - 100.0)
 * @param volumeScore          continuous factor score for relative volume (0.0 - 100.0)
 * @param trendContribution    weighted points contributed by trend (e.g. 0.25 * trendScore)
 * @param momentumContribution weighted points contributed by momentum (e.g. 0.25 * momentumScore)
 * @param rsiContribution      weighted points contributed by RSI (e.g. 0.25 * rsiScore)
 * @param volumeContribution   weighted points contributed by volume (e.g. 0.25 * volumeScore)
 * @param explanations         human-readable explainable bullets
 * @param signals              map of component name to evaluated Signal
 * @param ready                true if sufficient indicator data was available
 * @param timestamp            evaluation timestamp
 * @param rawScore             un-smoothed composite conviction score
 * @param smoothedScore        EMA-smoothed score (same as score)
 * @param realizedVolatility   measured rolling standard deviation of log returns
 */
public record ConvictionScore(
        String symbol,
        double score,
        ScoreCategory category,
        double trendScore,
        double momentumScore,
        double rsiScore,
        double volumeScore,
        double trendContribution,
        double momentumContribution,
        double rsiContribution,
        double volumeContribution,
        List<String> explanations,
        Map<String, Signal> signals,
        boolean ready,
        Instant timestamp,
        double rawScore,
        double smoothedScore,
        double realizedVolatility
) {
    public ConvictionScore {
        explanations = explanations != null ? Collections.unmodifiableList(explanations) : List.of();
        signals = signals != null ? Collections.unmodifiableMap(signals) : Map.of();
    }

    /**
     * Backwards-compatible 15-argument constructor.
     */
    public ConvictionScore(
            String symbol,
            double score,
            ScoreCategory category,
            double trendScore,
            double momentumScore,
            double rsiScore,
            double volumeScore,
            double trendContribution,
            double momentumContribution,
            double rsiContribution,
            double volumeContribution,
            List<String> explanations,
            Map<String, Signal> signals,
            boolean ready,
            Instant timestamp
    ) {
        this(
                symbol,
                score,
                category,
                trendScore,
                momentumScore,
                rsiScore,
                volumeScore,
                trendContribution,
                momentumContribution,
                rsiContribution,
                volumeContribution,
                explanations,
                signals,
                ready,
                timestamp,
                score,
                score,
                0.0
        );
    }

    public static ConvictionScore notReady(String symbol, Instant timestamp) {
        return new ConvictionScore(
                symbol,
                50.0,
                ScoreCategory.NEUTRAL,
                50.0,
                50.0,
                50.0,
                50.0,
                12.5,
                12.5,
                12.5,
                12.5,
                List.of("Warming up: collecting initial market data ticks"),
                Map.of(
                        "trend", Signal.NOT_READY,
                        "momentum", Signal.NOT_READY,
                        "rsi", Signal.NOT_READY,
                        "volume", Signal.NOT_READY
                ),
                false,
                timestamp != null ? timestamp : Instant.now(),
                50.0,
                50.0,
                0.0
        );
    }
}
