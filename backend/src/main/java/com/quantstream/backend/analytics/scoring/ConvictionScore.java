package com.quantstream.backend.analytics.scoring;

import com.quantstream.backend.analytics.indicator.Signal;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Immutable result of the Conviction Score evaluation.
 *
 * @param symbol               stock ticker symbol
 * @param score                composite score between 0.0 and 100.0
 * @param category             qualitative category (VERY_WEAK, WEAK, NEUTRAL, STRONG, VERY_STRONG)
 * @param trendContribution    weighted points contributed by trend/moving averages
 * @param momentumContribution weighted points contributed by price momentum
 * @param rsiContribution      weighted points contributed by RSI
 * @param volumeContribution   weighted points contributed by relative volume
 * @param explanations         human-readable explainable bullets
 * @param signals              map of component name to evaluated Signal
 * @param ready                true if sufficient indicator data was available
 * @param timestamp            evaluation timestamp
 */
public record ConvictionScore(
        String symbol,
        double score,
        ScoreCategory category,
        double trendContribution,
        double momentumContribution,
        double rsiContribution,
        double volumeContribution,
        List<String> explanations,
        Map<String, Signal> signals,
        boolean ready,
        Instant timestamp
) {
    public ConvictionScore {
        explanations = Collections.unmodifiableList(explanations);
        signals = Collections.unmodifiableMap(signals);
    }

    public static ConvictionScore notReady(String symbol, Instant timestamp) {
        return new ConvictionScore(
                symbol,
                50.0,
                ScoreCategory.NEUTRAL,
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
                timestamp != null ? timestamp : Instant.now()
        );
    }
}
