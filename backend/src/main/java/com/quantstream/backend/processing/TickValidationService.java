/*
 * ==================================================================================
 * FILE: TickValidationService.java
 * ==================================================================================
 *
 * WHAT THIS FILE DOES:
 * This is the "SECURITY GUARD" / "BOUNCER" of the data pipeline.
 *
 * In the real world, bad data happens all the time:
 *   - Network packets get corrupted
 *   - A third-party feed sends a negative price (e.g. price = -10.00)
 *   - A tick has an empty ticker symbol (e.g. symbol = "")
 *   - A message has a missing timestamp
 *
 * If bad data enters the Analytics Engine, mathematical formulas will divide by zero,
 * produce "NaN" (Not a Number), or crash background threads!
 *
 * This service inspects every incoming tick and throws an immediate exception if any
 * mandatory field is invalid, ensuring the rest of the application only ever touches
 * clean, trusted data.
 * ==================================================================================
 */

package com.quantstream.backend.processing;

import com.quantstream.backend.domain.StockTick;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
public class TickValidationService {

    /**
     * Inspects all fields of a StockTick.
     * If anything is missing or invalid, throws an IllegalArgumentException.
     * Otherwise returns the tick unchanged.
     */
    public StockTick validate(StockTick tick) {
        if (tick == null) {
            throw new IllegalArgumentException("StockTick must not be null");
        }
        if (tick.id() == null) {
            throw new IllegalArgumentException("StockTick id must not be null");
        }
        if (tick.symbol() == null || tick.symbol().isBlank()) {
            throw new IllegalArgumentException("StockTick symbol must not be blank");
        }
        BigDecimal price = tick.price();
        if (price == null || price.signum() <= 0) {
            throw new IllegalArgumentException("StockTick price must be positive");
        }
        if (tick.volume() < 0) {
            throw new IllegalArgumentException("StockTick volume must not be negative");
        }
        if (tick.timestamp() == null) {
            throw new IllegalArgumentException("StockTick timestamp must not be null");
        }
        if (tick.eventType() == null) {
            throw new IllegalArgumentException("StockTick event type must not be null");
        }
        if (tick.source() == null) {
            throw new IllegalArgumentException("StockTick source must not be null");
        }
        if (tick.exchange() == null || tick.exchange().isBlank()) {
            throw new IllegalArgumentException("StockTick exchange must not be blank");
        }
        return tick;
    }
}