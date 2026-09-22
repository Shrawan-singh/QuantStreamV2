/*
 * ==================================================================================
 * FILE: MockMarketDataProvider.java
 * ==================================================================================
 *
 * WHAT THIS FILE DOES:
 * This is the simulation engine's "Ticker Tape".
 *
 * WHEN IS THIS USED?
 * Whenever QuantStream is set to SIMULATION mode (the default mode).
 * In Spring, `@ConditionalOnProperty(name = "quantstream.marketdata.mode", havingValue = "simulation")`
 * tells the framework: "Only load this service if we are in simulation mode. If the user
 * configured live mode with Finnhub, completely ignore this file!"
 *
 * HOW IT WORKS (THE HEARTBEAT TIMER):
 * 1. When `connect()` is called, it starts a background clock (`ScheduledExecutorService`).
 * 2. Every X milliseconds (configured by `properties.getIntervalMs()`, e.g. every 500ms):
 *    - The timer wakes up.
 *    - Calls `emitNextTickSafely()`.
 *    - For every subscribed stock (e.g. RELIANCE, TCS, INFY), asks `DeterministicTickGenerator`
 *      for the next simulated price.
 *    - Hands each tick to the `tickListener` (which forwards it into the processing pipeline).
 * 3. When `disconnect()` is called, the timer stops cleanly.
 * ==================================================================================
 */

package com.quantstream.backend.marketdata.simulation;

import com.quantstream.backend.config.SimulationProperties;
import com.quantstream.backend.domain.StockTick;
import com.quantstream.backend.marketdata.MarketDataProvider;
import org.springframework.stereotype.Service;

import jakarta.annotation.PreDestroy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

@Service
@ConditionalOnProperty(name = "quantstream.marketdata.mode", havingValue = "simulation", matchIfMissing = true)
public class MockMarketDataProvider implements MarketDataProvider {

    private final SimulationProperties properties;
    private final DeterministicTickGenerator tickGenerator;
    private ScheduledExecutorService scheduler; // Background timer thread
    private final AtomicBoolean connected = new AtomicBoolean(false);
    private final AtomicInteger cursor = new AtomicInteger(0);

    // List of stocks we are currently generating simulated prices for
    private final List<String> subscribedSymbols = new ArrayList<>();

    // The callback listener where generated ticks will be sent (usually the TickPublisher)
    private volatile Consumer<StockTick> tickListener = tick -> { };

    public MockMarketDataProvider(SimulationProperties properties) {
        this.properties = properties;
        this.tickGenerator = new DeterministicTickGenerator(properties.getSeed(), properties.getSymbols());
        this.scheduler = Executors.newSingleThreadScheduledExecutor(new SimulationThreadFactory());
    }

    /**
     * Starts the simulation clock.
     */
    @Override
    public synchronized void connect() {
        // Atomic compareAndSet ensures we don't start the timer twice
        if (!connected.compareAndSet(false, true)) {
            return;
        }

        if (scheduler == null || scheduler.isShutdown()) {
            scheduler = Executors.newSingleThreadScheduledExecutor(new SimulationThreadFactory());
        }

        // Initialize with default simulation symbols if none were manually added
        synchronized (subscribedSymbols) {
            if (subscribedSymbols.isEmpty()) {
                subscribedSymbols.addAll(properties.getSymbols());
            }
        }

        // Schedule timer to run emitNextTickSafely every intervalMs (e.g. 500ms)
        scheduler.scheduleAtFixedRate(this::emitNextTickSafely, 0L, properties.getIntervalMs(), TimeUnit.MILLISECONDS);
    }

    /**
     * Adds a new stock symbol to the simulation loop.
     */
    @Override
    public void subscribe(String symbol) {
        synchronized (subscribedSymbols) {
            if (!subscribedSymbols.contains(symbol)) {
                subscribedSymbols.add(symbol);
            }
        }
    }

    /**
     * Removes a stock symbol from the simulation loop.
     */
    @Override
    public void unsubscribe(String symbol) {
        synchronized (subscribedSymbols) {
            subscribedSymbols.remove(symbol);
        }
    }

    /**
     * Stops the simulation clock and cancels scheduled tasks.
     */
    @Override
    public synchronized void disconnect() {
        if (connected.compareAndSet(true, false)) {
            if (scheduler != null && !scheduler.isShutdown()) {
                scheduler.shutdownNow();
            }
        }
    }

    @Override
    public String healthStatus() {
        return connected.get() ? "CONNECTED" : "DISCONNECTED";
    }

    @Override
    public void setTickListener(Consumer<StockTick> tickListener) {
        this.tickListener = tickListener == null ? tick -> { } : tickListener;
    }

    /**
     * Called automatically by Spring when the application shuts down.
     */
    @PreDestroy
    public void shutdown() {
        disconnect();
    }

    /**
     * The heartbeat method: iterates through all subscribed stocks, generates their next price,
     * and sends each tick to the listener.
     */
    private void emitNextTickSafely() {
        try {
            List<String> symbols;
            synchronized (subscribedSymbols) {
                if (subscribedSymbols.isEmpty()) {
                    return;
                }
                symbols = new ArrayList<>(subscribedSymbols);
            }

            for (String symbol : symbols) {
                StockTick tick = tickGenerator.nextTick(symbol);
                tickListener.accept(tick);
            }
        } catch (Exception ex) {
            System.err.println("[MockMarketDataProvider] Failed to emit simulated tick batch: " + ex.getMessage());
        }
    }

    private String nextSymbol() {
        synchronized (subscribedSymbols) {
            if (subscribedSymbols.isEmpty()) {
                return null;
            }

            int index = Math.floorMod(cursor.getAndIncrement(), subscribedSymbols.size());
            return subscribedSymbols.get(index);
        }
    }

    /**
     * Custom ThreadFactory naming the background thread "mock-market-data-provider"
     * so it's easy to identify in log files and thread dumps.
     */
    private static final class SimulationThreadFactory implements ThreadFactory {

        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "mock-market-data-provider");
            thread.setDaemon(false);
            return thread;
        }
    }
}
