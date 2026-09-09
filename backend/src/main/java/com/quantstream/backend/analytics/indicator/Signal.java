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
    POSITIVE,
    NEUTRAL,
    NEGATIVE,
    NOT_READY
}
