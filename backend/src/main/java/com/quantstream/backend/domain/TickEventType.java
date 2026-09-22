/*
 * ==================================================================================
 * FILE: TickEventType.java
 * ==================================================================================
 *
 * WHAT THIS FILE DOES:
 * This defines the different TYPES of market data events that can happen.
 * It's a simple list of categories — like labeling mail as "letter", "package",
 * or "postcard".
 *
 * JAVA CONCEPT - "enum":
 * An "enum" (short for enumeration) is a fixed list of possible values.
 * Think of it like a dropdown menu — you can ONLY pick from these options,
 * nothing else. This prevents typos and mistakes.
 * ==================================================================================
 */

package com.quantstream.backend.domain;

public enum TickEventType {
    TRADE,      // A real buy/sell transaction happened (e.g., someone bought 40 shares of AAPL at $338.76)
    QUOTE,      // A price update without an actual trade (just the bid/ask price changed)
    HEARTBEAT   // A "ping" message just to say "I'm still alive and connected" — no actual data
}
