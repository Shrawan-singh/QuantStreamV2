/*
 * ==================================================================================
 * FILE: ScoringProperties.java
 * ==================================================================================
 *
 * WHAT THIS FILE DOES:
 * Holds all mathematical weights and statistical tuning knobs for the Conviction Score engine.
 *
 * Spring automatically binds this to the YAML section: `quantstream.scoring`.
 *
 * PARAMETERS EXPLAINED:
 * - trendWeight:              Weight given to Trend (default 25%)
 * - momentumWeight:           Weight given to Momentum (default 25%)
 * - rsiWeight:                Weight given to RSI (default 25%)
 * - volumeWeight:             Weight given to Relative Volume (default 25%)
 * - volatilityLookback:       Rolling window size for standard deviation (default 20 ticks)
 * - minVolatilityLookback:    Minimum ticks needed before computing volatility (default 5)
 * - zScoreCap:                Maximum cap for standard deviation Z-scores (default 3.0)
 * - emaConfirmationBonus:     Points added when price is above both SMA & EMA (+4.0)
 * - emaDivergencePenalty:     Points deducted towards 50.0 when SMA & EMA disagree (3.0)
 * - smoothingAlpha:           EMA score smoothing rate (0.20 = 20% new, 80% previous)
 * - hysteresisBand:           Buffer band to prevent category flickering (+/- 1.5)
 * ==================================================================================
 */

package com.quantstream.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configurable weights and statistical parameters for the Conviction Score engine.
 */
@ConfigurationProperties(prefix = "quantstream.scoring")
public record ScoringProperties(
        double trendWeight,
        double momentumWeight,
        double rsiWeight,
        double volumeWeight,
        int volatilityLookback,
        int minVolatilityLookback,
        double zScoreCap,
        double emaConfirmationBonus,
        double emaDivergencePenalty,
        double smoothingAlpha,
        double hysteresisBand
) {
    public ScoringProperties {
        // Safe fallbacks in case configuration is omitted or contains invalid values
        if (trendWeight <= 0.0) trendWeight = 25.0;
        if (momentumWeight <= 0.0) momentumWeight = 25.0;
        if (rsiWeight <= 0.0) rsiWeight = 25.0;
        if (volumeWeight <= 0.0) volumeWeight = 25.0;
        if (volatilityLookback <= 0) volatilityLookback = 20;
        if (minVolatilityLookback <= 1) minVolatilityLookback = 5;
        if (zScoreCap <= 0.0) zScoreCap = 3.0;
        if (emaConfirmationBonus < 0.0) emaConfirmationBonus = 4.0;
        if (emaDivergencePenalty < 0.0) emaDivergencePenalty = 3.0;
        if (smoothingAlpha <= 0.0 || smoothingAlpha > 1.0) smoothingAlpha = 0.20;
        if (hysteresisBand < 0.0) hysteresisBand = 1.5;
    }

    public static ScoringProperties defaultProperties() {
        return new ScoringProperties(25.0, 25.0, 25.0, 25.0, 20, 5, 3.0, 4.0, 3.0, 0.20, 1.5);
    }

    /** Sum of all weights (used to normalize scores to a 0-100 scale) */
    public double totalWeight() {
        return trendWeight + momentumWeight + rsiWeight + volumeWeight;
    }
}
