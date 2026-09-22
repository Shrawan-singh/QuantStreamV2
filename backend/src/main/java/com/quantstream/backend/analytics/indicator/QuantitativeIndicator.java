/*
 * ==================================================================================
 * FILE: QuantitativeIndicator.java
 * ==================================================================================
 *
 * WHAT THIS FILE DOES:
 * This is an "Interface" (a contract or blueprint) for all technical indicators.
 *
 * In programming, an interface says:
 * "Any class that claims to be a QuantitativeIndicator MUST know how to do two things:"
 *   1. "getName()":   Tell us what it's called (e.g. "SMA", "EMA", "RSI", "MOMENTUM")
 *   2. "calculate()": Take a snapshot of the stock's recent price history and return
 *                     an "IndicatorResult" (value, signal, and ready status).
 *
 * WHY USE AN INTERFACE?
 * This allows the Analytics Engine to treat all indicators uniformly. If tomorrow
 * you want to add a brand-new indicator (like MACD or Bollinger Bands), you just write
 * a class that "implements QuantitativeIndicator" and the rest of the application
 * immediately knows how to run it without changing existing engine logic!
 * ==================================================================================
 */

package com.quantstream.backend.analytics.indicator;

import com.quantstream.backend.analytics.state.MarketState;

/**
 * Common abstraction for all quantitative technical indicators.
 */
public interface QuantitativeIndicator {

    /**
     * Unique identifier for this indicator (e.g., "SMA", "EMA", "RSI", "MOMENTUM", "RELATIVE_VOLUME").
     */
    String getName();

    /**
     * Calculates the indicator for the given market state snapshot.
     *
     * @param snapshot immutable point-in-time state of the stock (recent prices, volumes, etc.)
     * @return calculation result containing value, signal, and readiness flag
     */
    IndicatorResult calculate(MarketState.Snapshot snapshot);
}
