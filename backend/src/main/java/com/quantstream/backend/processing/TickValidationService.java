package com.quantstream.backend.processing;

import com.quantstream.backend.domain.StockTick;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
public class TickValidationService {

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