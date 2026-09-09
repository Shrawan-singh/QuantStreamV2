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
     * @param snapshot immutable point-in-time state of the stock
     * @return calculation result containing value, signal, and readiness flag
     */
    IndicatorResult calculate(MarketState.Snapshot snapshot);
}
