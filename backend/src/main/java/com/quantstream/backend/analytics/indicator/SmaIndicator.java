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
    private final int period;

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

    @Override
    public IndicatorResult calculate(MarketState.Snapshot snapshot) {
        if (snapshot == null || snapshot.recentPrices() == null) {
            return IndicatorResult.notReady();
        }

        List<BigDecimal> prices = snapshot.recentPrices();
        if (prices.size() < period) {
            return IndicatorResult.notReady();
        }

        // Sum the most recent 'period' prices
        BigDecimal sum = BigDecimal.ZERO;
        int startIdx = prices.size() - period;
        for (int i = startIdx; i < prices.size(); i++) {
            sum = sum.add(prices.get(i));
        }

        BigDecimal sma = sum.divide(BigDecimal.valueOf(period), 4, RoundingMode.HALF_UP);
        double smaVal = sma.doubleValue();

        // Interpret signal relative to latest price
        BigDecimal latestPrice = snapshot.latestPrice();
        Signal signal = Signal.NEUTRAL;
        if (latestPrice != null && smaVal > 0.0) {
            BigDecimal upperBand = sma.multiply(BigDecimal.valueOf(1.002));
            BigDecimal lowerBand = sma.multiply(BigDecimal.valueOf(0.998));
            if (latestPrice.compareTo(upperBand) > 0) {
                signal = Signal.POSITIVE;
            } else if (latestPrice.compareTo(lowerBand) < 0) {
                signal = Signal.NEGATIVE;
            }
        }

        return new IndicatorResult(roundTwoDecimals(smaVal), signal, true);
    }

    private double roundTwoDecimals(double val) {
        return BigDecimal.valueOf(val).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }
}
