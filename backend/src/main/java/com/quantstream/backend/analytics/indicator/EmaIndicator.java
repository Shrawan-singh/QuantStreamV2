/*
 * ==================================================================================
 * FILE: EmaIndicator.java
 * ==================================================================================
 *
 * WHAT THIS FILE DOES:
 * Calculates the "Exponential Moving Average" (EMA).
 *
 * SMA vs EMA — WHAT'S THE DIFFERENCE?
 * - In an SMA (Simple Moving Average), all 20 past days are treated equally.
 *   What happened 20 days ago has the exact same weight as what happened 5 minutes ago.
 * - In an EMA (Exponential Moving Average), MORE WEIGHT is given to the MOST RECENT prices!
 *   It reacts faster to sudden sharp price moves or breakouts than an SMA does.
 *
 * THE MATH FORMULA:
 * 1. Multiplier (k) = 2 / (period + 1)
 *    For a 20-period EMA, k = 2 / 21 = 0.0952 (meaning recent price gets ~9.5% weight)
 * 2. New EMA = (Current Price * k) + (Previous EMA * (1 - k))
 *
 * HOW THE TRAFFIC LIGHT (SIGNAL) IS DECIDED:
 *   - POSITIVE (Bullish): Latest price is > 0.2% above the EMA.
 *   - NEGATIVE (Bearish): Latest price is < 0.2% below the EMA.
 *   - NEUTRAL: Latest price is within +/- 0.2% band of the EMA.
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
 * Exponential Moving Average (EMA) indicator.
 *
 * <p>Formula:
 * <ul>
 *   <li>{@code Multiplier k = 2 / (period + 1)}</li>
 *   <li>Initial EMA = SMA of the first {@code period} observations</li>
 *   <li>{@code EMA_t = Price_t * k + EMA_(t-1) * (1 - k)}</li>
 * </ul>
 * </p>
 */
@Component
public class EmaIndicator implements QuantitativeIndicator {

    public static final String NAME = "EMA";
    private final int period;
    private final double multiplier;

    /*
     * Reads the EMA period (e.g. 20) from application.yml via IndicatorProperties.
     */
    @org.springframework.beans.factory.annotation.Autowired
    public EmaIndicator(IndicatorProperties properties) {
        this(properties != null ? properties.emaPeriod() : 20);
    }

    public EmaIndicator(int period) {
        if (period <= 0) {
            throw new IllegalArgumentException("EMA period must be positive");
        }
        this.period = period;
        // Smoothing factor multiplier: k = 2 / (N + 1)
        this.multiplier = 2.0 / (period + 1.0);
    }

    @Override
    public String getName() {
        return NAME;
    }

    public int getPeriod() {
        return period;
    }

    /**
     * Calculates the EMA iteratively over the historical price series.
     */
    @Override
    public IndicatorResult calculate(MarketState.Snapshot snapshot) {
        if (snapshot == null || snapshot.recentPrices() == null) {
            return IndicatorResult.notReady();
        }

        List<BigDecimal> prices = snapshot.recentPrices();
        // Warm-up check: we need at least 'period' prices to seed the calculation
        if (prices.size() < period) {
            return IndicatorResult.notReady();
        }

        // Step 1: Seed the very first EMA value using a simple average of the first 'period' prices
        double initialSmaSum = 0.0;
        for (int i = 0; i < period; i++) {
            initialSmaSum += prices.get(i).doubleValue();
        }
        double currentEma = initialSmaSum / period;

        // Step 2: Walk through all remaining prices one by one, applying the exponential formula
        for (int i = period; i < prices.size(); i++) {
            double price = prices.get(i).doubleValue();
            currentEma = (price * multiplier) + (currentEma * (1.0 - multiplier));
        }

        // Step 3: Interpret signal relative to latest price
        BigDecimal latestPrice = snapshot.latestPrice();
        Signal signal = Signal.NEUTRAL;
        if (latestPrice != null && currentEma > 0.0) {
            double upperBand = currentEma * 1.002; // +0.2%
            double lowerBand = currentEma * 0.998; // -0.2%
            double latest = latestPrice.doubleValue();
            if (latest > upperBand) {
                signal = Signal.POSITIVE; // Bullish breakout above EMA
            } else if (latest < lowerBand) {
                signal = Signal.NEGATIVE; // Bearish slide below EMA
            }
        }

        return new IndicatorResult(roundTwoDecimals(currentEma), signal, true);
    }

    private double roundTwoDecimals(double val) {
        return BigDecimal.valueOf(val).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }
}
