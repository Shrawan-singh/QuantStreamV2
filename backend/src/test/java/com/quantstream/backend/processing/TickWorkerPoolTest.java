package com.quantstream.backend.processing;

import com.quantstream.backend.config.StreamingProperties;
import com.quantstream.backend.domain.StockTick;
import com.quantstream.backend.domain.TickEventType;
import com.quantstream.backend.domain.TickSource;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TickWorkerPoolTest {

    @Test
    void workerPoolProcessesQueuedTicks() throws Exception {
        StreamingProperties properties = properties(3, 2);
        TickQueueService queueService = new TickQueueService(properties);
        TickProcessingService processingService = new TickProcessingService(new TickValidationService());
        TickWorkerPool workerPool = new TickWorkerPool(properties, queueService, processingService);

        workerPool.start();

        List<StockTick> ticks = List.of(
                tick("RELIANCE"),
                tick("TCS"),
                tick("INFY")
        );

        for (StockTick tick : ticks) {
            queueService.enqueue(tick);
        }

        Awaitility.await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                assertEquals(3L, processingService.getTotalProcessed()));
        assertEquals(1L, processingService.getProcessedForSymbol("RELIANCE"));
        assertEquals(1L, processingService.getProcessedForSymbol("TCS"));
        assertEquals(1L, processingService.getProcessedForSymbol("INFY"));

        workerPool.stop();
    }

    @Test
    void workerPoolStopsGracefully() {
        StreamingProperties properties = properties(2, 1);
        TickQueueService queueService = new TickQueueService(properties);
        TickProcessingService processingService = new TickProcessingService(new TickValidationService());
        TickWorkerPool workerPool = new TickWorkerPool(properties, queueService, processingService);

        workerPool.start();
        workerPool.stop();

        assertEquals(false, workerPool.isRunning());
    }

    private StreamingProperties properties(int queueSize, int workers) {
        StreamingProperties properties = new StreamingProperties();
        properties.setQueueSize(queueSize);
        properties.setWorkerPoolSize(workers);
        properties.setWorkerPollTimeoutMs(50L);
        properties.setShutdownTimeoutMs(1000L);
        return properties;
    }

    private StockTick tick(String symbol) {
        return new StockTick(
                UUID.randomUUID(),
                symbol,
                BigDecimal.valueOf(250.75),
                50_000L,
                Instant.now(),
                TickEventType.TRADE,
                TickSource.SIMULATION,
                "NSE"
        );
    }
}