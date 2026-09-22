/*
 * ==================================================================================
 * FILE: IndicatorProperties.java
 * ==================================================================================
 *
 * WHAT THIS FILE DOES:
 * Holds all configuration settings for technical indicators (SMA, EMA, RSI, Momentum, Volume).
 *
 * WHAT IS `@ConfigurationProperties(prefix = "quantstream.indicators")`?
 * Instead of hardcoding numbers like "20" or "14" directly in Java code, Spring Boot
 * automatically reads these values from your `application.yml` or `.env` file!
 * For example:
 *   quantstream.indicators.sma-period=20
 *
 * DEFAULT VALUES AND SAFEGUARDS:
 * The compact constructor below validates each parameter. If someone accidentally puts
 * `smaPeriod = -5` in a configuration file, the code catches it and resets it safely to 20!
 * ==================================================================================
 */

package com.quantstream.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration parameters for quantitative indicators.
 */
@ConfigurationProperties(prefix = "quantstream.indicators")
public record IndicatorProperties(
        int smaPeriod,                     // Default: 20 ticks
        int emaPeriod,                     // Default: 20 ticks
        int rsiPeriod,                     // Default: 14 ticks
        double rsiPositiveThreshold,       // Default: 60.0 (bullish threshold)
        double rsiNegativeThreshold,       // Default: 40.0 (bearish threshold)
        int momentumPeriod,                // Default: 10 ticks lookback
        double momentumPositiveThreshold,  // Default: +0.5% rate of change
        double momentumNegativeThreshold,  // Default: -0.5% rate of change
        int volumeLookback,                // Default: 20 ticks baseline
        double volumePositiveThreshold,    // Default: 1.5x of baseline volume
        double volumeNegativeThreshold     // Default: 0.7x of baseline volume
) {
    public IndicatorProperties {
        // Fallback safety guards: ensure positive, valid values
        if (smaPeriod <= 0) smaPeriod = 20;
        if (emaPeriod <= 0) emaPeriod = 20;
        if (rsiPeriod <= 0) rsiPeriod = 14;
        if (rsiPositiveThreshold <= 0.0) rsiPositiveThreshold = 60.0;
        if (rsiNegativeThreshold <= 0.0) rsiNegativeThreshold = 40.0;
        if (momentumPeriod <= 0) momentumPeriod = 10;
        if (momentumPositiveThreshold <= 0.0) momentumPositiveThreshold = 0.5;
        if (momentumNegativeThreshold >= 0.0) momentumNegativeThreshold = -0.5;
        if (volumeLookback <= 0) volumeLookback = 20;
        if (volumePositiveThreshold <= 0.0) volumePositiveThreshold = 1.5;
        if (volumeNegativeThreshold <= 0.0) volumeNegativeThreshold = 0.7;
    }

    /**
     * Default factory providing standard baseline values for tests.
     */
    public static IndicatorProperties defaultProperties() {
        return new IndicatorProperties(
                20, 20, 14, 60.0, 40.0, 10, 0.5, -0.5, 20, 1.5, 0.7
        );
    }
}
