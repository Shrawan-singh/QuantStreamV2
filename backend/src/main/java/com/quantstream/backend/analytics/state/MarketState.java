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

    public static final int DEFAULT_MAX_WINDOW_SIZE = 100;

    private final String symbol;
    private final int maxWindowSize;

    private final Deque<BigDecimal> recentPrices;
    private final Deque<Long> recentVolumes;

    private BigDecimal latestPrice;
    private BigDecimal previousPrice;
    private BigDecimal openPrice;
    private BigDecimal highPrice;
    private BigDecimal lowPrice;
    private long latestVolume;
    private long cumulativeVolume;
    private long tickCount;
    private Instant latestTimestamp;

    // Rolling sum for fast SMA calculation
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

        this.previousPrice = this.latestPrice != null ? this.latestPrice : price;
        this.latestPrice = price;
        this.latestVolume = volume;
        this.latestTimestamp = timestamp;
        this.tickCount++;
        this.cumulativeVolume += volume;

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

        // Maintain bounded price window and rolling sum
        if (recentPrices.size() >= maxWindowSize) {
            BigDecimal evictedPrice = recentPrices.removeFirst();
            priceRollingSum = priceRollingSum.subtract(evictedPrice);
        }
        recentPrices.addLast(price);
        priceRollingSum = priceRollingSum.add(price);

        // Maintain bounded volume window and rolling sum
        if (recentVolumes.size() >= maxWindowSize) {
            long evictedVolume = recentVolumes.removeFirst();
            volumeRollingSum -= evictedVolume;
        }
        recentVolumes.addLast(volume);
        volumeRollingSum += volume;

        return getSnapshot();
    }

    /**
     * Takes a point-in-time immutable snapshot of the market state.
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

    private BigDecimal calculatePriceChange() {
        if (latestPrice == null || openPrice == null) {
            return BigDecimal.ZERO;
        }
        return latestPrice.subtract(openPrice);
    }

    private double calculatePriceChangePercent() {
        if (latestPrice == null || openPrice == null || openPrice.compareTo(BigDecimal.ZERO) == 0) {
            return 0.0;
        }
        return latestPrice.subtract(openPrice)
                .divide(openPrice, 6, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .doubleValue();
    }

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
            recentPrices = Collections.unmodifiableList(recentPrices);
            recentVolumes = Collections.unmodifiableList(recentVolumes);
        }

        public int windowSize() {
            return recentPrices.size();
        }
    }
}
