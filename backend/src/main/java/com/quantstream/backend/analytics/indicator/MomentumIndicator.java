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
    private final int period;
    private final double positiveThreshold;
    private final double negativeThreshold;

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
        if (prices.size() < period + 1) {
            return IndicatorResult.notReady();
        }

        BigDecimal current = prices.get(prices.size() - 1);
        BigDecimal reference = prices.get(prices.size() - 1 - period);

        if (reference.compareTo(BigDecimal.ZERO) <= 0) {
            return IndicatorResult.notReady();
        }

        BigDecimal changePercent = current.subtract(reference)
                .divide(reference, 6, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));

        double momentumVal = changePercent.doubleValue();

        Signal signal;
        if (momentumVal > positiveThreshold) {
            signal = Signal.POSITIVE;
        } else if (momentumVal < negativeThreshold) {
            signal = Signal.NEGATIVE;
        } else {
            signal = Signal.NEUTRAL;
        }

        return new IndicatorResult(roundTwoDecimals(momentumVal), signal, true);
    }

    private double roundTwoDecimals(double val) {
        return BigDecimal.valueOf(val).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }
}
