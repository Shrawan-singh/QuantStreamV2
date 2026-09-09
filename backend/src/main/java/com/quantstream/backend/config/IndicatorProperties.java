package com.quantstream.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration parameters for quantitative indicators.
 */
@ConfigurationProperties(prefix = "quantstream.indicators")
public record IndicatorProperties(
        int smaPeriod,
        int emaPeriod,
        int rsiPeriod,
        double rsiPositiveThreshold,
        double rsiNegativeThreshold,
        int momentumPeriod,
        double momentumPositiveThreshold,
        double momentumNegativeThreshold,
        int volumeLookback,
        double volumePositiveThreshold,
        double volumeNegativeThreshold
) {
    public IndicatorProperties {
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

    public static IndicatorProperties defaultProperties() {
        return new IndicatorProperties(
                20, 20, 14, 60.0, 40.0, 10, 0.5, -0.5, 20, 1.5, 0.7
        );
    }
}
