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
    private final int lookback;
    private final double positiveThreshold;
    private final double negativeThreshold;

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
        if (volumes.size() < lookback) {
            return IndicatorResult.notReady();
        }

        long sum = 0L;
        int startIdx = volumes.size() - lookback;
        for (int i = startIdx; i < volumes.size(); i++) {
            sum += volumes.get(i);
        }

        double avgVolume = (double) sum / lookback;
        if (avgVolume <= 0.0) {
            return IndicatorResult.notReady();
        }

        long currentVolume = snapshot.latestVolume();
        double rvol = (double) currentVolume / avgVolume;

        Signal signal;
        if (rvol > positiveThreshold) {
            signal = Signal.POSITIVE;
        } else if (rvol < negativeThreshold) {
            signal = Signal.NEGATIVE;
        } else {
            signal = Signal.NEUTRAL;
        }

        return new IndicatorResult(roundTwoDecimals(rvol), signal, true);
    }

    private double roundTwoDecimals(double val) {
        return BigDecimal.valueOf(val).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }
}
