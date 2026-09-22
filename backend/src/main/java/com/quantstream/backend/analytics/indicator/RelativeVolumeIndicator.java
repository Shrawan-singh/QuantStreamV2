/*
 * ==================================================================================
 * FILE: RelativeVolumeIndicator.java
 * ==================================================================================
 *
 * WHAT THIS FILE DOES:
 * Calculates the "Relative Volume" (RVOL).
 *
 * WHY VOLUME MATTERS IN TRADING:
 * Volume is the number of shares traded. If a stock price moves up 2% on tiny volume
 * (just 100 shares), it might be an illusion or a fluke. But if it moves up 2% on
 * 5,000,000 shares, institutional investors (like big hedge funds or banks) are piling in!
 *
 * WHAT DOES RVOL MEAN?
 * RVOL compares the CURRENT trade volume to the AVERAGE baseline volume over the
 * past 20 ticks:
 *   Relative Volume (RVOL) = Current Volume / Average Volume
 *
 * HOW THE TRAFFIC LIGHT (SIGNAL) IS DECIDED:
 *   - POSITIVE (High Participation): RVOL > 1.5x
 *     (50% more volume than usual! Big players are active).
 *   - NEGATIVE (Drying Up / Low Liquidity): RVOL < 0.7x
 *     (Volume is 30% below average; very few trades happening).
 *   - NEUTRAL (Typical): RVOL between 0.7x and 1.5x
 *     (Normal everyday market activity).
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
 * Relative Volume (RVOL) indicator measuring trading activity relative to rolling baseline.
 *
 * <p>Formula:
 * {@code RelativeVolume = CurrentVolume / AverageVolume(Lookback)}
 * </p>
 *
 * <p>Signal Interpretation:
 * <ul>
 *   <li>POSITIVE: RVOL &gt; 1.5x (elevated market participation)</li>
 *   <li>NEGATIVE: RVOL &lt; 0.7x (thin liquidity / subdued activity)</li>
 *   <li>NEUTRAL: 0.7x &lt;= RVOL &lt;= 1.5x (typical baseline range)</li>
 * </ul>
 * </p>
 */
@Component
public class RelativeVolumeIndicator implements QuantitativeIndicator {

    public static final String NAME = "RELATIVE_VOLUME";
    private final int lookback;                  // Lookback window for volume baseline (default: 20 ticks)
    private final double positiveThreshold;      // Default: 1.5x
    private final double negativeThreshold;      // Default: 0.7x

    @org.springframework.beans.factory.annotation.Autowired
    public RelativeVolumeIndicator(IndicatorProperties properties) {
        this(
                properties != null ? properties.volumeLookback() : 20,
                properties != null ? properties.volumePositiveThreshold() : 1.5,
                properties != null ? properties.volumeNegativeThreshold() : 0.7
        );
    }

    public RelativeVolumeIndicator(int lookback, double positiveThreshold, double negativeThreshold) {
        if (lookback <= 0) {
            throw new IllegalArgumentException("Volume lookback must be positive");
        }
        this.lookback = lookback;
        this.positiveThreshold = positiveThreshold;
        this.negativeThreshold = negativeThreshold;
    }

    @Override
    public String getName() {
        return NAME;
    }

    public int getLookback() {
        return lookback;
    }

    @Override
    public IndicatorResult calculate(MarketState.Snapshot snapshot) {
        if (snapshot == null || snapshot.recentVolumes() == null) {
            return IndicatorResult.notReady();
        }

        List<Long> volumes = snapshot.recentVolumes();
        // Warm-up check: we must have collected at least 'lookback' volume entries
        if (volumes.size() < lookback) {
            return IndicatorResult.notReady();
        }

        // Sum up the past 'lookback' volumes to calculate their average
        long sum = 0L;
        int startIdx = volumes.size() - lookback;
        for (int i = startIdx; i < volumes.size(); i++) {
            sum += volumes.get(i);
        }

        double avgVolume = (double) sum / lookback;
        if (avgVolume <= 0.0) {
            return IndicatorResult.notReady();
        }

        // Compare the most recent volume against the average baseline
        long currentVolume = snapshot.latestVolume();
        double rvol = (double) currentVolume / avgVolume;

        // Interpret signal
        Signal signal;
        if (rvol > positiveThreshold) {
            signal = Signal.POSITIVE; // > 1.5x normal volume
        } else if (rvol < negativeThreshold) {
            signal = Signal.NEGATIVE; // < 0.7x normal volume
        } else {
            signal = Signal.NEUTRAL;  // between 0.7x and 1.5x
        }

        return new IndicatorResult(roundTwoDecimals(rvol), signal, true);
    }

    private double roundTwoDecimals(double val) {
        return BigDecimal.valueOf(val).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }
}
