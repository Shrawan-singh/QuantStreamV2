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
     *
     * @param tick incoming stock tick
     * @return an immutable point-in-time snapshot of the state after applying the tick
     */
    public MarketState.Snapshot update(StockTick tick) {
        if (tick == null || tick.symbol() == null) {
            throw new IllegalArgumentException("Tick and tick symbol must not be null");
        }
        MarketState state = states.computeIfAbsent(
                tick.symbol().toUpperCase(),
                sym -> new MarketState(sym, maxWindowSize)
        );
        return state.update(tick);
    }

    /**
     * Retrieves the current snapshot for a given symbol, if present.
     */
    public Optional<MarketState.Snapshot> getSnapshot(String symbol) {
        if (symbol == null) {
            return Optional.empty();
        }
        MarketState state = states.get(symbol.toUpperCase());
        return state != null ? Optional.of(state.getSnapshot()) : Optional.empty();
    }

    /**
     * Retrieves snapshots for all currently tracked symbols.
     */
    public List<MarketState.Snapshot> getAllSnapshots() {
        List<MarketState.Snapshot> snapshots = new ArrayList<>(states.size());
        for (MarketState state : states.values()) {
            snapshots.add(state.getSnapshot());
        }
        return Collections.unmodifiableList(snapshots);
    }

    /**
     * Returns the set of all tracked symbol names.
     */
    public Set<String> getSymbols() {
        return Collections.unmodifiableSet(states.keySet());
    }

    /**
     * Resets all market states (primarily for testing).
     */
    public void clear() {
        states.clear();
    }

    /**
     * Returns the total number of tracked symbols.
     */
    public int size() {
        return states.size();
    }
}
