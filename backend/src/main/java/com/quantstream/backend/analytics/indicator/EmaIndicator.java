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

    @org.springframework.beans.factory.annotation.Autowired
    public EmaIndicator(IndicatorProperties properties) {
        this(properties != null ? properties.emaPeriod() : 20);
    }

    public EmaIndicator(int period) {
        if (period <= 0) {
            throw new IllegalArgumentException("EMA period must be positive");
        }
        this.period = period;
        this.multiplier = 2.0 / (period + 1.0);
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
        if (prices.size() < period) {
            return IndicatorResult.notReady();
        }

        // Seed with SMA of the initial 'period' prices
        double initialSmaSum = 0.0;
        for (int i = 0; i < period; i++) {
            initialSmaSum += prices.get(i).doubleValue();
        }
        double currentEma = initialSmaSum / period;

        // Iteratively calculate EMA for remaining prices
        for (int i = period; i < prices.size(); i++) {
            double price = prices.get(i).doubleValue();
            currentEma = (price * multiplier) + (currentEma * (1.0 - multiplier));
        }

        // Signal interpretation
        BigDecimal latestPrice = snapshot.latestPrice();
        Signal signal = Signal.NEUTRAL;
        if (latestPrice != null && currentEma > 0.0) {
            double upperBand = currentEma * 1.002;
            double lowerBand = currentEma * 0.998;
            double latest = latestPrice.doubleValue();
            if (latest > upperBand) {
                signal = Signal.POSITIVE;
            } else if (latest < lowerBand) {
                signal = Signal.NEGATIVE;
            }
        }

        return new IndicatorResult(roundTwoDecimals(currentEma), signal, true);
    }

    private double roundTwoDecimals(double val) {
        return BigDecimal.valueOf(val).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }
}
