package com.quantstream.backend.processing;

import com.quantstream.backend.config.StreamingProperties;
import com.quantstream.backend.domain.StockTick;
import com.quantstream.backend.domain.TickEventType;
import com.quantstream.backend.domain.TickSource;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class TickQueueServiceTest {

    @Test
    void enqueuesAndDequeuesTick() throws Exception {
        TickQueueService queueService = new TickQueueService(properties(2));
        StockTick tick = validTick("RELIANCE");

        queueService.enqueue(tick);

        assertEquals(1, queueService.size());
        assertEquals(tick, queueService.take(100));
        assertEquals(0, queueService.size());
    }

    @Test
    void shutdownStopsAcceptingNewTicks() {
        TickQueueService queueService = new TickQueueService(properties(1));
        queueService.shutdown();

        assertFalse(queueService.isAccepting());
    }

    private StreamingProperties properties(int queueSize) {
        StreamingProperties properties = new StreamingProperties();
        properties.setQueueSize(queueSize);
        properties.setWorkerPoolSize(1);
        properties.setWorkerPollTimeoutMs(50L);
        properties.setShutdownTimeoutMs(100L);
        return properties;
    }

    private StockTick validTick(String symbol) {
        return new StockTick(
                UUID.randomUUID(),
                symbol,
                BigDecimal.valueOf(100.25),
                1_000L,
                Instant.now(),
                TickEventType.TRADE,
                TickSource.SIMULATION,
                "NSE"
        );
    }
}