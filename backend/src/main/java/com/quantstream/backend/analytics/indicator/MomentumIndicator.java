/*
 * ==================================================================================
 * FILE: MomentumIndicator.java
 * ==================================================================================
 *
 * WHAT THIS FILE DOES:
 * Calculates the percentage Rate of Change (Momentum) of a stock over a lookback window.
 *
 * REAL WORLD ANALOGY:
 * Think of measuring a car's acceleration. You compare the car's speed right now
 * with its speed 10 seconds ago:
 *   - If speed jumped from 50 mph to 70 mph -> Strong positive momentum (+40%)!
 *   - If speed dropped from 50 mph to 40 mph -> Negative momentum (-20%)!
 *
 * THE MATH FORMULA:
 *   Momentum (%) = ((Current Price - Price N ticks ago) / Price N ticks ago) * 100
 *
 * HOW THE TRAFFIC LIGHT (SIGNAL) IS DECIDED:
 * We use a default lookback of 10 periods:
 *   - POSITIVE: Momentum > +0.5% (Price is actively surging upward).
 *   - NEGATIVE: Momentum < -0.5% (Price is actively dropping).
 *   - NEUTRAL:  Momentum between -0.5% and +0.5% (Flat or very slow move).
 * ==================================================================================
 */

package com.quantstream.backend.analytics.indicator;

import com.quantstream.backend.analytics.state.MarketState;
import com.quantstream.backend.config.IndicatorProperties;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Momentum indicator measuring percentage rate of change over a lookback window.
 *
 * <p>Formula:
 * {@code Momentum = ((Price_t - Price_(t-N)) / Price_(t-N)) * 100}
 * </p>
 *
 * <p>Signal Interpretation:
 * <ul>
 *   <li>POSITIVE: Momentum &gt; +0.5%</li>
 *   <li>NEGATIVE: Momentum &lt; -0.5%</li>
 *   <li>NEUTRAL: -0.5% &lt;= Momentum &lt;= +0.5%</li>
 * </ul>
 * </p>
 */
@Component
public class MomentumIndicator implements QuantitativeIndicator {

    public static final String NAME = "MOMENTUM";
    private final int period;                    // Number of ticks to look back (default: 10)
    private final double positiveThreshold;      // Default: +0.5%
    private final double negativeThreshold;      // Default: -0.5%

    @org.springframework.beans.factory.annotation.Autowired
    public MomentumIndicator(IndicatorProperties properties) {
        this(
                properties != null ? properties.momentumPeriod() : 10,
                properties != null ? properties.momentumPositiveThreshold() : 0.5,
                properties != null ? properties.momentumNegativeThreshold() : -0.5
        );
    }

    public MomentumIndicator(int period, double positiveThreshold, double negativeThreshold) {
        if (period <= 0) {
            throw new IllegalArgumentException("Momentum period must be positive");
        }
        this.period = period;
        this.positiveThreshold = positiveThreshold;
        this.negativeThreshold = negativeThreshold;
    }

    @Override
    public String getName() {
        return NAME;
    }

    public int getPeriod() {
        return period;
    }

    @Override
    public IndicatorResult calculate(MarketState.Snapshot snapshot) {
        if (snapshot == null || snapshot.recentPrices() == null) {
            return IndicatorResult.notReady();
        }

        List<BigDecimal> prices = snapshot.recentPrices();
        // Warm-up check: need at least (period + 1) prices to compare current vs N ticks ago
        if (prices.size() < period + 1) {
            return IndicatorResult.notReady();
        }

        BigDecimal current = prices.get(prices.size() - 1);           // Most recent price
        BigDecimal reference = prices.get(prices.size() - 1 - period); // Price from N ticks ago

        // Safety check to avoid division by zero or negative price
        if (reference.compareTo(BigDecimal.ZERO) <= 0) {
            return IndicatorResult.notReady();
        }

        // Percentage change = ((current - reference) / reference) * 100
        BigDecimal changePercent = current.subtract(reference)
                .divide(reference, 6, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));

        double momentumVal = changePercent.doubleValue();

        // Interpret signal based on threshold bands
        Signal signal;
        if (momentumVal > positiveThreshold) {
            signal = Signal.POSITIVE; // > +0.5%
        } else if (momentumVal < negativeThreshold) {
            signal = Signal.NEGATIVE; // < -0.5%
        } else {
            signal = Signal.NEUTRAL;  // between -0.5% and +0.5%
        }

        return new IndicatorResult(roundTwoDecimals(momentumVal), signal, true);
    }

    private double roundTwoDecimals(double val) {
        return BigDecimal.valueOf(val).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }
}
