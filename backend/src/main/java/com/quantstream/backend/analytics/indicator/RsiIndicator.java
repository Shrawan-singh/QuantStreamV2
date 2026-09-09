package com.quantstream.backend.analytics.indicator;

import com.quantstream.backend.analytics.state.MarketState;
import com.quantstream.backend.config.IndicatorProperties;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Relative Strength Index (RSI) indicator based on Wilder's smoothing technique.
 *
 * <p>Formula:
 * <ul>
 *   <li>{@code RS = Smoothed Average Gain / Smoothed Average Loss}</li>
 *   <li>{@code RSI = 100 - (100 / (1 + RS))}</li>
 * </ul>
 * </p>
 *
 * <p>Signal Interpretation:
 * <ul>
 *   <li>POSITIVE: RSI &gt; 60 (bullish momentum)</li>
 *   <li>NEGATIVE: RSI &lt; 40 (bearish momentum)</li>
 *   <li>NEUTRAL: 40 &lt;= RSI &lt;= 60</li>
 * </ul>
 * </p>
 */
@Component
public class RsiIndicator implements QuantitativeIndicator {

    public static final String NAME = "RSI";
    private final int period;
    private final double positiveThreshold;
    private final double negativeThreshold;

    @org.springframework.beans.factory.annotation.Autowired
    public RsiIndicator(IndicatorProperties properties) {
        this(
                properties != null ? properties.rsiPeriod() : 14,
                properties != null ? properties.rsiPositiveThreshold() : 60.0,
                properties != null ? properties.rsiNegativeThreshold() : 40.0
        );
    }

    public RsiIndicator(int period, double positiveThreshold, double negativeThreshold) {
        if (period <= 0) {
            throw new IllegalArgumentException("RSI period must be positive");
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
        // Requires at least period + 1 prices to form 'period' delta transitions
        if (prices.size() < period + 1) {
            return IndicatorResult.notReady();
        }

        // Calculate initial period gains and losses
        double sumGain = 0.0;
        double sumLoss = 0.0;
        for (int i = 1; i <= period; i++) {
            double delta = prices.get(i).doubleValue() - prices.get(i - 1).doubleValue();
            if (delta > 0) {
                sumGain += delta;
            } else {
                sumLoss += Math.abs(delta);
            }
        }

        double avgGain = sumGain / period;
        double avgLoss = sumLoss / period;

        // Apply Wilder's smoothing for any remaining price deltas
        for (int i = period + 1; i < prices.size(); i++) {
            double delta = prices.get(i).doubleValue() - prices.get(i - 1).doubleValue();
            double gain = delta > 0 ? delta : 0.0;
            double loss = delta < 0 ? Math.abs(delta) : 0.0;

            avgGain = ((avgGain * (period - 1)) + gain) / period;
            avgLoss = ((avgLoss * (period - 1)) + loss) / period;
        }

        double rsi;
        if (avgLoss == 0.0) {
            rsi = (avgGain == 0.0) ? 50.0 : 100.0;
        } else {
            double rs = avgGain / avgLoss;
            rsi = 100.0 - (100.0 / (1.0 + rs));
        }

        Signal signal;
        if (rsi > positiveThreshold) {
            signal = Signal.POSITIVE;
        } else if (rsi < negativeThreshold) {
            signal = Signal.NEGATIVE;
        } else {
            signal = Signal.NEUTRAL;
        }

        return new IndicatorResult(roundTwoDecimals(rsi), signal, true);
    }

    private double roundTwoDecimals(double val) {
        return BigDecimal.valueOf(val).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }
}
