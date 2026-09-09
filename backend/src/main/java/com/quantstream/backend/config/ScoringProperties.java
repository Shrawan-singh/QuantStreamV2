package com.quantstream.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configurable weights and options for the Conviction Score engine.
 */
@ConfigurationProperties(prefix = "quantstream.scoring")
public record ScoringProperties(
        double trendWeight,
        double momentumWeight,
        double rsiWeight,
        double volumeWeight
) {
    public ScoringProperties {
        if (trendWeight <= 0.0) trendWeight = 25.0;
        if (momentumWeight <= 0.0) momentumWeight = 25.0;
        if (rsiWeight <= 0.0) rsiWeight = 25.0;
        if (volumeWeight <= 0.0) volumeWeight = 25.0;
    }

    public static ScoringProperties defaultProperties() {
        return new ScoringProperties(25.0, 25.0, 25.0, 25.0);
    }

    public double totalWeight() {
        return trendWeight + momentumWeight + rsiWeight + volumeWeight;
    }
}
