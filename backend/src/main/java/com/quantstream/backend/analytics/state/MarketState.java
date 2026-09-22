/*
 * ==================================================================================
 * FILE: MarketState.java
 * ==================================================================================
 *
 * WHAT THIS FILE DOES:
 * This is the IN-MEMORY NOTEBOOK for a SINGLE stock (e.g. just for "AAPL").
 *
 * Think of it as a live chalkboard that constantly updates as new trade ticks arrive:
 *   - "What was the latest price?" -> latestPrice
 *   - "What was the opening price today?" -> openPrice
 *   - "What was the highest price reached?" -> highPrice
 *   - "What was the lowest price?" -> lowPrice
 *   - "How many total shares traded today?" -> cumulativeVolume
 *   - "What were the last 100 prices that happened?" -> recentPrices (a rolling list)
 *
 * THE ROLLING WINDOW (SLIDING QUEUE):
 * We don't want to keep a billion prices in memory until the computer crashes.
 * Instead, we keep a bounded window of the last 100 prices (maxWindowSize).
 * When price #101 arrives, the oldest price (#1) is removed ("evicted") from the front!
 *
 * O(1) FAST MATH TRICK (Rolling Sums):
 * Instead of looping through all 100 prices every single millisecond to compute averages:
 *   new_sum = old_sum - evicted_oldest_price + new_incoming_price
 * That takes 1 tiny CPU instruction instead of 100!
 *
 * THREAD SAFETY (SYNCHRONIZED):
 * Multiple background threads might receive ticks for the same stock at the same microsecond.
 * The "synchronized" keyword acts like a lock on a restroom door: only one thread can enter
 * and update this stock's chalkboard at a time, preventing race conditions or corrupted numbers.
 *
 * IMMUTABLE SNAPSHOT:
 * After updating the chalkboard, it creates a frozen "Snapshot" copy and hands it to the
 * indicators. That way, the indicators can take their time doing math without worrying
 * that the price changed midway through their calculation!
 * ==================================================================================
 */

package com.quantstream.backend.analytics.state;

import com.quantstream.backend.domain.StockTick;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;

/**
 * Thread-safe, per-symbol in-memory market state container.
 *
 * <p>Maintains bounded historical windows of recent prices and volumes,
 * along with session statistics (open, high, low, cumulative volume, tick count)
 * and rolling sums for efficient O(1) moving average computation.</p>
 *
 * <p>Synchronization is localized per symbol instance to avoid global locks
 * across different stocks.</p>
 */
public class MarketState {

    // By default, we keep history of the last 100 ticks per stock
    public static final int DEFAULT_MAX_WINDOW_SIZE = 100;

    private final String symbol;
    private final int maxWindowSize;

    // Double-ended queues (Deques) allow fast adding to the back and removing from the front
    private final Deque<BigDecimal> recentPrices;
    private final Deque<Long> recentVolumes;

    // Live session variables
    private BigDecimal latestPrice;
    private BigDecimal previousPrice;
    private BigDecimal openPrice;
    private BigDecimal highPrice;
    private BigDecimal lowPrice;
    private long latestVolume;
    private long cumulativeVolume;
    private long tickCount;
    private Instant latestTimestamp;

    // Rolling sums for fast O(1) SMA calculation
    private BigDecimal priceRollingSum = BigDecimal.ZERO;
    private long volumeRollingSum = 0L;

    public MarketState(String symbol) {
        this(symbol, DEFAULT_MAX_WINDOW_SIZE);
    }

    public MarketState(String symbol, int maxWindowSize) {
        if (symbol == null || symbol.isBlank()) {
            throw new IllegalArgumentException("Symbol must not be null or blank");
        }
        if (maxWindowSize <= 0) {
            throw new IllegalArgumentException("maxWindowSize must be positive");
        }
        this.symbol = symbol.trim().toUpperCase();
        this.maxWindowSize = maxWindowSize;
        this.recentPrices = new ArrayDeque<>(maxWindowSize);
        this.recentVolumes = new ArrayDeque<>(maxWindowSize);
    }

    /**
     * Updates this market state with an incoming tick.
     * Synchronized to guarantee atomic updates to prices, statistics, rolling sums,
     * and immediate generation of an immutable snapshot under the same lock monitor.
     *
     * @param tick incoming stock tick
     * @return immutable point-in-time snapshot reflecting the update
     */
    public synchronized Snapshot update(StockTick tick) {
        if (tick == null) {
            return getSnapshot();
        }

        BigDecimal price = tick.price();
        long volume = tick.volume();
        Instant timestamp = tick.timestamp();

        // Save previous price before updating to the new one
        this.previousPrice = this.latestPrice != null ? this.latestPrice : price;
        this.latestPrice = price;
        this.latestVolume = volume;
        this.latestTimestamp = timestamp;
        this.tickCount++;
        this.cumulativeVolume += volume;

        // Initialize or update session High, Low, and Open prices
        if (this.openPrice == null) {
            this.openPrice = price;
            this.highPrice = price;
            this.lowPrice = price;
        } else {
            if (price.compareTo(this.highPrice) > 0) {
                this.highPrice = price;
            }
            if (price.compareTo(this.lowPrice) < 0) {
                this.lowPrice = price;
            }
        }

        // Maintain bounded price window (remove oldest price if window is full)
        if (recentPrices.size() >= maxWindowSize) {
            BigDecimal evictedPrice = recentPrices.removeFirst();
            priceRollingSum = priceRollingSum.subtract(evictedPrice);
        }
        recentPrices.addLast(price);
        priceRollingSum = priceRollingSum.add(price);

        // Maintain bounded volume window (remove oldest volume if window is full)
        if (recentVolumes.size() >= maxWindowSize) {
            long evictedVolume = recentVolumes.removeFirst();
            volumeRollingSum -= evictedVolume;
        }
        recentVolumes.addLast(volume);
        volumeRollingSum += volume;

        // Return a fresh frozen snapshot with the new state
        return getSnapshot();
    }

    /**
     * Takes a point-in-time immutable snapshot of the market state.
     * Safe to pass to any other thread because the lists are copied and cannot be modified.
     */
    public synchronized Snapshot getSnapshot() {
        return new Snapshot(
                symbol,
                latestPrice,
                previousPrice,
                openPrice,
                highPrice,
                lowPrice,
                calculatePriceChange(),
                calculatePriceChangePercent(),
                latestVolume,
                cumulativeVolume,
                tickCount,
                latestTimestamp,
                new ArrayList<>(recentPrices),
                new ArrayList<>(recentVolumes),
                priceRollingSum,
                volumeRollingSum
        );
    }

    // Absolute price difference: (latestPrice - openPrice)
    private BigDecimal calculatePriceChange() {
        if (latestPrice == null || openPrice == null) {
            return BigDecimal.ZERO;
        }
        return latestPrice.subtract(openPrice);
    }

    // Percentage price difference: ((latestPrice - openPrice) / openPrice) * 100
    private double calculatePriceChangePercent() {
        if (latestPrice == null || openPrice == null || openPrice.compareTo(BigDecimal.ZERO) == 0) {
            return 0.0;
        }
        return latestPrice.subtract(openPrice)
                .divide(openPrice, 6, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .doubleValue();
    }

    // ===== GETTER METHODS =====

    public String getSymbol() {
        return symbol;
    }

    public synchronized int getPriceCount() {
        return recentPrices.size();
    }

    public synchronized BigDecimal getLatestPrice() {
        return latestPrice;
    }

    public synchronized long getLatestVolume() {
        return latestVolume;
    }

    public synchronized Instant getLatestTimestamp() {
        return latestTimestamp;
    }

    /**
     * Immutable snapshot of MarketState for consumption by indicators and downstream services.
     * Because this is a Java "record", it cannot be mutated once created.
     */
    public record Snapshot(
            String symbol,
            BigDecimal latestPrice,
            BigDecimal previousPrice,
            BigDecimal openPrice,
            BigDecimal highPrice,
            BigDecimal lowPrice,
            BigDecimal priceChange,
            double priceChangePercent,
            long latestVolume,
            long cumulativeVolume,
            long tickCount,
            Instant latestTimestamp,
            List<BigDecimal> recentPrices,
            List<Long> recentVolumes,
            BigDecimal priceRollingSum,
            long volumeRollingSum
    ) {
        public Snapshot {
            // Wrap in unmodifiable lists so nobody can accidentally call list.add() or list.clear()
            recentPrices = Collections.unmodifiableList(recentPrices);
            recentVolumes = Collections.unmodifiableList(recentVolumes);
        }

        public int windowSize() {
            return recentPrices.size();
        }
    }
}
