package com.quantstream.backend.analytics.state;

import com.quantstream.backend.domain.StockTick;
import com.quantstream.backend.domain.TickEventType;
import com.quantstream.backend.domain.TickSource;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Concurrency test for {@link MarketStateStore} and {@link MarketState}.
 *
 * <p>Validates that high-throughput concurrent tick updates across multiple symbols
 * execute with zero race conditions, correct per-symbol isolation, and strictly bounded memory windows.</p>
 */
class MarketStateConcurrencyTest {

    @Test
    void concurrentTickUpdatesAcrossSymbolsMaintainIsolationAndConsistency() throws InterruptedException {
        int windowSize = 50;
        MarketStateStore store = new MarketStateStore(windowSize);

        List<String> symbols = List.of("RELIANCE", "TCS", "INFY", "HDFCBANK", "ICICIBANK");
        int ticksPerSymbol = 1000;
        int totalTicks = symbols.size() * ticksPerSymbol;

        int numThreads = 10;
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(totalTicks);

        for (String sym : symbols) {
            for (int i = 1; i <= ticksPerSymbol; i++) {
                final double price = 100.0 + (i % 20);
                final long volume = 100L;
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        StockTick tick = new StockTick(
                                UUID.randomUUID(),
                                sym,
                                BigDecimal.valueOf(price),
                                volume,
                                Instant.now(),
                                TickEventType.TRADE,
                                TickSource.SIMULATION,
                                "NSE"
                        );
                        store.update(tick);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }
        }

        // Release all threads simultaneously
        startLatch.countDown();
        assertTrue(doneLatch.await(10, TimeUnit.SECONDS), "Concurrent tick submission timed out");

        executor.shutdown();
        assertTrue(executor.awaitTermination(2, TimeUnit.SECONDS));

        // Verify state store properties
        assertEquals(5, store.size());
        assertEquals(symbols.size(), store.getSymbols().size());

        for (String sym : symbols) {
            MarketState.Snapshot snap = store.getSnapshot(sym).orElseThrow();
            // Total ticks processed for symbol must be exact
            assertEquals(ticksPerSymbol, snap.tickCount());
            // Cumulative volume must be exact (1000 * 100L = 100,000L)
            assertEquals(100_000L, snap.cumulativeVolume());
            // Window size must be strictly bounded to maxWindowSize
            assertEquals(windowSize, snap.recentPrices().size());
            assertEquals(windowSize, snap.recentVolumes().size());
            // Price range check
            assertTrue(snap.highPrice().doubleValue() >= 100.0);
            assertTrue(snap.lowPrice().doubleValue() <= 120.0);
        }
    }
}
