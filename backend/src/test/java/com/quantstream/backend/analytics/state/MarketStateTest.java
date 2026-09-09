package com.quantstream.backend.analytics.state;

import com.quantstream.backend.domain.StockTick;
import com.quantstream.backend.domain.TickEventType;
import com.quantstream.backend.domain.TickSource;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class MarketStateTest {

    private StockTick createTick(String symbol, double price, long volume) {
        return new StockTick(
                UUID.randomUUID(),
                symbol,
                BigDecimal.valueOf(price),
                volume,
                Instant.now(),
                TickEventType.TRADE,
                TickSource.SIMULATION,
                "NSE"
        );
    }

    @Test
    void updatesStatsCorrectlyAcrossTicks() {
        MarketState state = new MarketState("INFY", 5);

        state.update(createTick("INFY", 100.0, 1000));
        state.update(createTick("INFY", 110.0, 1500));
        state.update(createTick("INFY", 95.0, 2000));

        MarketState.Snapshot snapshot = state.getSnapshot();
        assertEquals("INFY", snapshot.symbol());
        assertEquals(new BigDecimal("95.0"), snapshot.latestPrice());
        assertEquals(new BigDecimal("100.0"), snapshot.openPrice());
        assertEquals(new BigDecimal("110.0"), snapshot.highPrice());
        assertEquals(new BigDecimal("95.0"), snapshot.lowPrice());
        assertEquals(new BigDecimal("-5.0"), snapshot.priceChange());
        assertEquals(-5.0, snapshot.priceChangePercent(), 0.001);
        assertEquals(4500, snapshot.cumulativeVolume());
        assertEquals(3, snapshot.tickCount());
        assertEquals(3, snapshot.windowSize());
        assertEquals(new BigDecimal("305.0"), snapshot.priceRollingSum());
    }

    @Test
    void evictsOldestItemsWhenWindowCapacityReached() {
        MarketState state = new MarketState("TCS", 3);

        state.update(createTick("TCS", 10.0, 100));
        state.update(createTick("TCS", 20.0, 200));
        state.update(createTick("TCS", 30.0, 300));
        assertEquals(3, state.getPriceCount());

        // 4th tick should evict 10.0
        state.update(createTick("TCS", 40.0, 400));
        MarketState.Snapshot snapshot = state.getSnapshot();

        assertEquals(3, snapshot.windowSize());
        assertEquals(new BigDecimal("40.0"), snapshot.latestPrice());
        // recentPrices should now be [20.0, 30.0, 40.0] -> sum = 90.0
        assertEquals(new BigDecimal("90.0"), snapshot.priceRollingSum());
        assertEquals(900, snapshot.volumeRollingSum());
        // Cumulative volume keeps running total -> 100+200+300+400 = 1000
        assertEquals(1000, snapshot.cumulativeVolume());
    }

    @Test
    void threadSafeConcurrentUpdates() throws InterruptedException {
        MarketStateStore store = new MarketStateStore(50);
        int threadCount = 8;
        int ticksPerThread = 100;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    for (int j = 0; j < ticksPerThread; j++) {
                        store.update(createTick("RELIANCE", 2500.0 + j, 100));
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        assertTrue(latch.await(5, TimeUnit.SECONDS));
        executor.shutdown();

        MarketState.Snapshot snapshot = store.getSnapshot("RELIANCE").orElseThrow();
        assertEquals(threadCount * ticksPerThread, snapshot.tickCount());
        assertEquals(50, snapshot.windowSize());
    }
}
