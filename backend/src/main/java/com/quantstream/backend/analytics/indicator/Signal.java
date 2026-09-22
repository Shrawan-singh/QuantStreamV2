/*
 * ==================================================================================
 * FILE: Signal.java
 * ==================================================================================
 *
 * WHAT THIS FILE DOES:
 * This enum defines the "Traffic Light" system for technical indicators in QuantStream.
 *
 * Each indicator (like SMA, RSI, or Momentum) analyzes market data and outputs a Signal:
 *   - POSITIVE:  Green light! Bullish condition (e.g. price is rising, strong momentum).
 *   - NEUTRAL:   Yellow light! Sideways or normal condition (neither bullish nor bearish).
 *   - NEGATIVE:  Red light! Bearish condition (e.g. price is falling below average).
 *   - NOT_READY: Gray light! The indicator is still "warming up" and needs more price
 *                data ticks before it can compute a valid signal.
 *
 * IMPORTANT NOTE:
 * These are analytical classifications designed to help users understand what the
 * mathematical indicators are saying, NOT financial advice or automated trade recommendations.
 * ==================================================================================
 */

package com.quantstream.backend.analytics.indicator;

/**
 * Represents the interpreted direction of a quantitative indicator signal.
 *
 * <ul>
 *   <li>{@code POSITIVE} — indicator suggests favorable/bullish conditions</li>
 *   <li>{@code NEUTRAL} — indicator is within normal/ambiguous range</li>
 *   <li>{@code NEGATIVE} — indicator suggests unfavorable/bearish conditions</li>
 *   <li>{@code NOT_READY} — insufficient data to calculate the indicator</li>
 * </ul>
 *
 * <p>These signals are analytical classifications, NOT trading recommendations.</p>
 */
public enum Signal {
    POSITIVE,   // Bullish / Favorable
    NEUTRAL,    // Flat / In-between / Ambiguous
    NEGATIVE,   // Bearish / Unfavorable
    NOT_READY   // Waiting for more data ticks to arrive
}
