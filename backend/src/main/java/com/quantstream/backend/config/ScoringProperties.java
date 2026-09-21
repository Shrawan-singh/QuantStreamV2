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

    public double totalWeight() {
        return trendWeight + momentumWeight + rsiWeight + volumeWeight;
    }
}
