package com.quantstream.backend.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

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
