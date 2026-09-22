/*
 * ==================================================================================
 * FILE: StockTick.java
 * ==================================================================================
 *
 * WHAT THIS FILE DOES:
 * A "StockTick" represents ONE single price update for a stock.
 *
 * Every time a stock's price changes (someone buys or sells shares), the stock
 * exchange generates a "tick" — a tiny message saying "AAPL is now $338.76,
 * 40 shares were traded, at 12:38 PM."
 *
 * This file defines the SHAPE of that message — what information each tick carries.
 * Think of it as a form template: every tick fills in the same fields.
 *
 * HOW IT'S USED:
 * - In SIMULATION mode: The DeterministicTickGenerator creates fake ticks
 * - In LIVE mode: The FinnhubTradeParser converts real market data into ticks
 * - Both flow through the processing pipeline → analytics → frontend
 *
 * JAVA CONCEPT - "record":
 * A "record" is a special kind of class in Java that's just a container for data.
 * It's like a labeled box — you can put data in and read it out, but you can't
 * change it after creation (it's "immutable"). This is good because we don't want
 * anyone accidentally changing a stock tick after it's been created.
 * ==================================================================================
 */

package com.quantstream.backend.domain;

import java.math.BigDecimal;  // A precise number type for money (avoids rounding errors that regular doubles have)
import java.time.Instant;     // A point in time (like a timestamp), e.g., "2026-09-21T16:38:53Z"
import java.util.UUID;        // A Universally Unique ID — a random string like "550e8400-e29b-41d4-a716-446655440000"

/*
 * This is the StockTick record. Each field stores one piece of information:
 *
 *   id        → A unique identifier for this specific tick (so we can tell ticks apart)
 *   symbol    → The stock ticker, e.g., "AAPL" for Apple, "TSLA" for Tesla
 *   price     → The current price of the stock, e.g., 338.76
 *   volume    → How many shares were traded in this tick, e.g., 40
 *   timestamp → When this tick happened, e.g., "2026-09-21T16:38:53Z"
 *   eventType → What kind of event this is: TRADE (actual sale), QUOTE (price update), or HEARTBEAT (keep-alive ping)
 *   source    → Where this tick came from: SIMULATION (fake data) or LIVE_PROVIDER (real Finnhub market data)
 *   exchange  → Which stock exchange, e.g., "NSE" (India), "NASDAQ" (USA), "NYSE" (USA)
 */
public record StockTick(
        UUID id,
        String symbol,
        BigDecimal price,
        long volume,
        Instant timestamp,
        TickEventType eventType,
        TickSource source,
        String exchange
) {
}
