/*
 * ==================================================================================
 * FILE: SmaIndicator.java
 * ==================================================================================
 *
 * WHAT THIS FILE DOES:
 * Calculates the "Simple Moving Average" (SMA).
 *
 * REAL WORLD ANALOGY:
 * Imagine tracking your daily calorie intake over the last 20 days. You add up all
 * 20 days and divide by 20. That gives you your average.
 * In trading:
 *   SMA = (Price_1 + Price_2 + ... + Price_20) / 20
 *
 * WHY TRADERS USE IT:
 * Stock prices bounce up and down constantly (noise). The SMA smooths out the noise
 * to reveal the true underlying trend.
 *
 * HOW THE TRAFFIC LIGHT (SIGNAL) IS DECIDED:
 * We compare the CURRENT stock price to the SMA line:
 *   - POSITIVE (Bullish): Current price is > 0.2% ABOVE the SMA (stock is running hot/upward).
 *   - NEGATIVE (Bearish): Current price is > 0.2% BELOW the SMA (stock is sliding down).
 *   - NEUTRAL (Sideways): Current price is within +/- 0.2% of the SMA (hovering around average).
 *
 * WARM-UP REQUIREMENT:
 * If we need a 20-period SMA, but only 10 price ticks have arrived so far, we cannot
 * calculate it yet! In that case, it returns "IndicatorResult.notReady()".
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
 * Simple Moving Average (SMA) indicator.
 *
 * <p>Formula: {@code SMA = (P_1 + P_2 + ... + P_N) / N}</p>
 *
 * <p>Signal Interpretation:
 * <ul>
 *   <li>POSITIVE: latest price is &gt; 0.2% above SMA (bullish trend confirmation)</li>
 *   <li>NEGATIVE: latest price is &lt; 0.2% below SMA (bearish trend confirmation)</li>
 *   <li>NEUTRAL: latest price is within +/-0.2% band of SMA</li>
 * </ul>
 * </p>
 */
@Component
public class SmaIndicator implements QuantitativeIndicator {

    public static final String NAME = "SMA";
    private final int period; // Default is usually 20 periods

    /*
     * Spring DI Constructor:
     * Spring automatically injects the configured "period" from application.yml
     * (e.g. quantstream.analytics.indicators.sma-period=20).
     */
    @org.springframework.beans.factory.annotation.Autowired
    public SmaIndicator(IndicatorProperties properties) {
        this(properties != null ? properties.smaPeriod() : 20);
    }

    public SmaIndicator(int period) {
        if (period <= 0) {
            throw new IllegalArgumentException("SMA period must be positive");
        }
        this.period = period;
    }

    @Override
    public String getName() {
        return NAME;
    }

    public int getPeriod() {
        return period;
    }

    /**
     * Calculates the SMA from the recent price history in the snapshot.
     */
    @Override
    public IndicatorResult calculate(MarketState.Snapshot snapshot) {
        if (snapshot == null || snapshot.recentPrices() == null) {
            return IndicatorResult.notReady();
        }

        List<BigDecimal> prices = snapshot.recentPrices();
        // Warm-up check: we must have at least 'period' (e.g. 20) prices collected
        if (prices.size() < period) {
            return IndicatorResult.notReady();
        }

        // Sum up the most recent 'period' prices (e.g. the last 20 prices)
        BigDecimal sum = BigDecimal.ZERO;
        int startIdx = prices.size() - period;
        for (int i = startIdx; i < prices.size(); i++) {
            sum = sum.add(prices.get(i));
        }

        // Divide sum by period to get the average
        BigDecimal sma = sum.divide(BigDecimal.valueOf(period), 4, RoundingMode.HALF_UP);
        double smaVal = sma.doubleValue();

        // Interpret signal relative to the latest live price
        BigDecimal latestPrice = snapshot.latestPrice();
        Signal signal = Signal.NEUTRAL;
        if (latestPrice != null && smaVal > 0.0) {
            // Upper band is 0.2% above SMA (+0.002)
            BigDecimal upperBand = sma.multiply(BigDecimal.valueOf(1.002));
            // Lower band is 0.2% below SMA (-0.002)
            BigDecimal lowerBand = sma.multiply(BigDecimal.valueOf(0.998));

            if (latestPrice.compareTo(upperBand) > 0) {
                signal = Signal.POSITIVE; // Price broken above SMA
            } else if (latestPrice.compareTo(lowerBand) < 0) {
                signal = Signal.NEGATIVE; // Price broken below SMA
            }
        }

        // Return the rounded SMA value and signal, with ready=true
        return new IndicatorResult(roundTwoDecimals(smaVal), signal, true);
    }

    /**
     * Helper to round numbers to 2 decimal places (e.g., 234.5678 -> 234.57).
     */
    private double roundTwoDecimals(double val) {
        return BigDecimal.valueOf(val).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }
}
