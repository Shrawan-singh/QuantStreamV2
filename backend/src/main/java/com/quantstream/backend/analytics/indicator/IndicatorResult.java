
package com.quantstream.backend.analytics.indicator;

/**
 * Immutable result of a single indicator calculation.
 *
 * @param value  the computed indicator value (e.g., RSI=64.2, SMA=2885.40)
 * @param signal the interpreted signal direction
 * @param ready  {@code true} if the indicator had enough data to compute a meaningful value;
 *               {@code false} during warm-up (value will be 0.0 and signal will be NOT_READY)
 */
public record IndicatorResult(
        double value,
        Signal signal,
        boolean ready
) {

    /** Factory method for an indicator that does not yet have enough data. */
    public static IndicatorResult notReady() {
        return new IndicatorResult(0.0, Signal.NOT_READY, false);
    }
}
