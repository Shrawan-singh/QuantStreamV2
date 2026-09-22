/*
 * ==================================================================================
 * FILE: ConvictionScore.java
 * ==================================================================================
 *
 * WHAT THIS FILE DOES:
 * This is the ultimate "Report Card" for a stock's quantitative health.
 *
 * Rather than giving the user a black-box mystery number, QuantStream's Conviction
 * Score is 100% EXPLAINABLE! Every point in the final score is broken down into its
 * individual ingredients:
 *
 * 1. THE FOUR FACTORS (Each graded 0 to 100):
 *    - trendScore:    Is the price above its moving averages (SMA/EMA)?
 *    - momentumScore: How fast is the price rising or falling?
 *    - rsiScore:      Is buying pressure outpacing selling pressure?
 *    - volumeScore:   Are institutional traders backing this move with heavy volume?
 *
 * 2. WEIGHTED CONTRIBUTIONS:
 *    Each factor contributes a portion (default: 25% each) towards the 100-point total:
 *    Final Score = (trend * 0.25) + (momentum * 0.25) + (rsi * 0.25) + (volume * 0.25)
 *
 * 3. EXPLAINABILITY BULLETS ("explanations"):
 *    Plain-English sentences explaining exactly WHY the score moved.
 *    For example: "Trend: 74.2 (Price $235.00 vs SMA $228.10, divergence: +3.02%)"
 *
 * 4. SMOOTHING AND VOLATILITY:
 *    - rawScore:           The instantaneous raw score computed right this second.
 *    - smoothedScore:      An Exponential Moving Average of recent scores to stop the
 *                          score from jumping violently on every tiny noise tick.
 *    - realizedVolatility: How wild or jumpy this stock's prices have actually been.
 * ==================================================================================
 */

package com.quantstream.backend.analytics.scoring;

import com.quantstream.backend.analytics.indicator.Signal;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Immutable result of the continuous Conviction Score evaluation.
 *
 * @param symbol               stock ticker symbol (e.g. "AAPL")
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
        // Ensure collections cannot be modified from the outside
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

    /**
     * Helper factory: returns a neutral 50.0 score while the system is warming up.
     * When the app first turns on, it doesn't have enough past prices, so it displays
     * a clean "Warming up" status instead of throwing errors.
     */
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
