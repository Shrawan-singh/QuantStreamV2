/*
 * ==================================================================================
 * FILE: IndicatorResult.java
 * ==================================================================================
 *
 * WHAT THIS FILE DOES:
 * When an indicator (like 20-day Simple Moving Average, or 14-day RSI) finishes its
 * calculation, it wraps its output into this simple package: "IndicatorResult".
 *
 * WHAT'S INSIDE THE PACKAGE:
 * 1. "value":  The actual calculated mathematical number.
 *              Examples: SMA = 245.50, RSI = 68.2, Momentum = +1.4%
 *
 * 2. "signal": The qualitative traffic light (POSITIVE, NEUTRAL, NEGATIVE, or NOT_READY).
 *              Example: If RSI is 72, the signal is POSITIVE.
 *
 * 3. "ready":  A boolean (true/false) flag.
 *              - true:  We have collected enough historical prices to compute a valid result.
 *              - false: We are still collecting data (warm-up phase), so value is 0.0
 *                       and signal is NOT_READY.
 *
 * WHY A RECORD?
 * Java "record" is an immutable data carrier. Once created, its values cannot be
 * tampered with or changed by another thread, making it safe for concurrent calculations.
 * ==================================================================================
 */

package com.quantstream.backend.analytics.indicator;

/**
 * Immutable result of a single indicator calculation.
 *
 * @param value  the computed indicator value (e.g., RSI=64.2, SMA=2885.40)
 * @param signal the interpreted signal direction (POSITIVE, NEUTRAL, NEGATIVE, NOT_READY)
 * @param ready  {@code true} if the indicator had enough data to compute a meaningful value;
 *               {@code false} during warm-up (value will be 0.0 and signal will be NOT_READY)
 */
public record IndicatorResult(
        double value,
        Signal signal,
        boolean ready
) {

    /**
     * Convenience factory helper: creates a default "not ready yet" result.
     * Used when the system hasn't received enough ticks yet (e.g., during the first few seconds of startup).
     */
    public static IndicatorResult notReady() {
        return new IndicatorResult(0.0, Signal.NOT_READY, false);
    }
}
