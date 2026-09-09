package com.quantstream.backend.processing;

import com.quantstream.backend.domain.StockTick;
import com.quantstream.backend.domain.TickEventType;
import com.quantstream.backend.domain.TickSource;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TickValidationServiceTest {

    private final TickValidationService validationService = new TickValidationService();

    @Test
    void acceptsValidTick() {
        StockTick tick = validTick();

        StockTick validated = validationService.validate(tick);

        assertEquals(tick, validated);
    }

    @Test
    void rejectsNegativePrice() {
        StockTick invalid = new StockTick(
                UUID.randomUUID(),
                "RELIANCE",
                BigDecimal.valueOf(-1),
                100L,
                Instant.now(),
                TickEventType.TRADE,
                TickSource.SIMULATION,
                "NSE"
        );

        assertThrows(IllegalArgumentException.class, () -> validationService.validate(invalid));
    }

    private StockTick validTick() {
        return new StockTick(
                UUID.randomUUID(),
                "RELIANCE",
                BigDecimal.valueOf(2900.25),
                100_000L,
                Instant.now(),
                TickEventType.TRADE,
                TickSource.SIMULATION,
                "NSE"
        );
    }
}