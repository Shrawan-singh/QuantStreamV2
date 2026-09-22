/*
 * ==================================================================================
 * FILE: RsiIndicator.java
 * ==================================================================================
 *
 * WHAT THIS FILE DOES:
 * Calculates the "Relative Strength Index" (RSI), originally developed by J. Welles Wilder.
 *
 * WHAT DOES RSI ACTUALLY MEASURE?
 * RSI measures the SPEED and CHANGE of price movements on a scale from 0 to 100.
 * Think of it like a speedometer for stock price action:
 *   - On days/ticks when the price went UP: how big were the gains?
 *   - On days/ticks when the price went DOWN: how big were the losses?
 *
 * It compares average gains against average losses:
 *   RS (Relative Strength) = Average Gain / Average Loss
 *   RSI = 100 - (100 / (1 + RS))
 *
 * WHAT THE NUMBERS MEAN:
 * - 0 to 100 scale:
 *   - RSI > 60: Bulls are in control, price has been climbing strongly (POSITIVE signal).
 *   - RSI between 40 and 60: Balanced / Normal chop (NEUTRAL signal).
 *   - RSI < 40: Bears are in control, price has been tumbling (NEGATIVE signal).
 *   - (In traditional trading, RSI > 70 is often considered "overbought" and RSI < 30 "oversold").
 *
 * WARM-UP REQUIREMENT:
 * To calculate 14 price changes (deltas), we need at least 15 prices (period + 1).
 * If we have fewer than 15, we return "IndicatorResult.notReady()".
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
    private final int period;                    // Typically 14
    private final double positiveThreshold;      // Default: 60.0
    private final double negativeThreshold;      // Default: 40.0

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
        // Requires at least period + 1 prices to form 'period' delta transitions (e.g. 15 prices for 14 deltas)
        if (prices.size() < period + 1) {
            return IndicatorResult.notReady();
        }

        // Step 1: Calculate initial period gains and losses
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

        // Step 2: Apply Wilder's smoothing technique for any subsequent prices beyond the initial period
        // Wilder's smoothing formula: NewAvg = ((OldAvg * (N - 1)) + CurrentValue) / N
        for (int i = period + 1; i < prices.size(); i++) {
            double delta = prices.get(i).doubleValue() - prices.get(i - 1).doubleValue();
            double gain = delta > 0 ? delta : 0.0;
            double loss = delta < 0 ? Math.abs(delta) : 0.0;

            avgGain = ((avgGain * (period - 1)) + gain) / period;
            avgLoss = ((avgLoss * (period - 1)) + loss) / period;
        }

        // Step 3: Compute RS and RSI
        double rsi;
        if (avgLoss == 0.0) {
            // If the stock only went up and never had any loss, RSI is 100 (max possible)
            rsi = (avgGain == 0.0) ? 50.0 : 100.0;
        } else {
            double rs = avgGain / avgLoss;
            rsi = 100.0 - (100.0 / (1.0 + rs));
        }

        // Step 4: Map RSI number to traffic-light Signal
        Signal signal;
        if (rsi > positiveThreshold) {
            signal = Signal.POSITIVE; // > 60: Bullish
        } else if (rsi < negativeThreshold) {
            signal = Signal.NEGATIVE; // < 40: Bearish
        } else {
            signal = Signal.NEUTRAL;  // 40 - 60: In-between
        }

        return new IndicatorResult(roundTwoDecimals(rsi), signal, true);
    }

    private double roundTwoDecimals(double val) {
        return BigDecimal.valueOf(val).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }
}
