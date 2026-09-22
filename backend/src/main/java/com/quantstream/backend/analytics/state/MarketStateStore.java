/*
 * ==================================================================================
 * FILE: MarketStateStore.java
 * ==================================================================================
 *
 * WHAT THIS FILE DOES:
 * If "MarketState" is the chalkboard for ONE stock, then "MarketStateStore" is
 * the ENTIRE SCHOOL OF CHALKBOARDS!
 *
 * It is a central, thread-safe in-memory collection holding the MarketState for
 * every stock QuantStream is actively tracking.
 *
 * HOW IT WORKS (ConcurrentHashMap):
 * - Key:   Stock Ticker (e.g. "AAPL", "MSFT", "RELIANCE")
 * - Value: The MarketState chalkboard for that specific stock
 *
 * WHAT HAPPENS WHEN A NEW TICK ARRIVES:
 * 1. The worker thread calls `marketStateStore.update(tick)`.
 * 2. `computeIfAbsent(...)`: If we have never seen "AAPL" before, it automatically
 *    creates a new blank MarketState chalkboard for AAPL on the fly.
 * 3. It applies the tick to AAPL's chalkboard and returns the frozen snapshot.
 *
 * THREAD SAFETY WITHOUT BOTTLENECKS:
 * A normal HashMap will crash or corrupt data if 10 threads write to it simultaneously.
 * "ConcurrentHashMap" is a special Java data structure that splits the table into buckets.
 * Updating AAPL does NOT block another thread updating NVDA! They run in parallel at full speed.
 * ==================================================================================
 */

package com.quantstream.backend.analytics.state;

import com.quantstream.backend.domain.StockTick;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe concurrent in-memory store for all tracked market states.
 *
 * <p>Uses {@link ConcurrentHashMap} with computeIfAbsent to ensure lock-free
 * state retrieval and symbol-isolated mutation. Individual state synchronization
 * is handled internally by {@link MarketState}.</p>
 */
@Component
public class MarketStateStore {

    // Central concurrent dictionary: Symbol String -> MarketState instance
    private final ConcurrentHashMap<String, MarketState> states = new ConcurrentHashMap<>();
    private final int maxWindowSize;

    public MarketStateStore() {
        this(MarketState.DEFAULT_MAX_WINDOW_SIZE);
    }

    public MarketStateStore(int maxWindowSize) {
        this.maxWindowSize = maxWindowSize;
    }

    /**
     * Updates the market state for the symbol in the given tick.
     * If the symbol hasn't been seen yet, creates a new MarketState automatically.
     *
     * @param tick incoming stock tick
     * @return an immutable point-in-time snapshot of the state after applying the tick
     */
    public MarketState.Snapshot update(StockTick tick) {
        if (tick == null || tick.symbol() == null) {
            throw new IllegalArgumentException("Tick and tick symbol must not be null");
        }
        // "computeIfAbsent" safely checks if symbol exists in map; if not, instantiates a new MarketState
        MarketState state = states.computeIfAbsent(
                tick.symbol().toUpperCase(),
                sym -> new MarketState(sym, maxWindowSize)
        );
        return state.update(tick);
    }

    /**
     * Retrieves the current snapshot for a single symbol (e.g. "AAPL").
     * Returns an Optional, which is empty if the symbol has never received any ticks.
     */
    public Optional<MarketState.Snapshot> getSnapshot(String symbol) {
        if (symbol == null) {
            return Optional.empty();
        }
        MarketState state = states.get(symbol.toUpperCase());
        return state != null ? Optional.of(state.getSnapshot()) : Optional.empty();
    }

    /**
     * Retrieves frozen snapshots for all currently tracked symbols across the whole system.
     */
    public List<MarketState.Snapshot> getAllSnapshots() {
        List<MarketState.Snapshot> snapshots = new ArrayList<>(states.size());
        for (MarketState state : states.values()) {
            snapshots.add(state.getSnapshot());
        }
        return Collections.unmodifiableList(snapshots);
    }

    /**
     * Returns the set of all tracked symbol names (e.g., ["AAPL", "MSFT", "NVDA", ...]).
     */
    public Set<String> getSymbols() {
        return Collections.unmodifiableSet(states.keySet());
    }

    /**
     * Clears all in-memory states (useful when restarting tests or switching modes).
     */
    public void clear() {
        states.clear();
    }

    /**
     * Returns the total number of currently tracked symbols.
     */
    public int size() {
        return states.size();
    }
}
