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
    private ScheduledExecutorService scheduler;
    private final AtomicBoolean connected = new AtomicBoolean(false);
    private final AtomicInteger cursor = new AtomicInteger(0);
    private final List<String> subscribedSymbols = new ArrayList<>();
    private volatile Consumer<StockTick> tickListener = tick -> { };

    public MockMarketDataProvider(SimulationProperties properties) {
        this.properties = properties;
        this.tickGenerator = new DeterministicTickGenerator(properties.getSeed(), properties.getSymbols());
        this.scheduler = Executors.newSingleThreadScheduledExecutor(new SimulationThreadFactory());
    }

    @Override
    public synchronized void connect() {
        if (!connected.compareAndSet(false, true)) {
            return;
        }

        if (scheduler == null || scheduler.isShutdown()) {
            scheduler = Executors.newSingleThreadScheduledExecutor(new SimulationThreadFactory());
        }

        synchronized (subscribedSymbols) {
            if (subscribedSymbols.isEmpty()) {
                subscribedSymbols.addAll(properties.getSymbols());
            }
        }

        scheduler.scheduleAtFixedRate(this::emitNextTickSafely, 0L, properties.getIntervalMs(), TimeUnit.MILLISECONDS);
    }

    @Override
    public void subscribe(String symbol) {
        synchronized (subscribedSymbols) {
            if (!subscribedSymbols.contains(symbol)) {
                subscribedSymbols.add(symbol);
            }
        }
    }

    @Override
    public void unsubscribe(String symbol) {
        synchronized (subscribedSymbols) {
            subscribedSymbols.remove(symbol);
        }
    }

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

    @PreDestroy
    public void shutdown() {
        disconnect();
    }

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

    private static final class SimulationThreadFactory implements ThreadFactory {

        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "mock-market-data-provider");
            thread.setDaemon(false);
            return thread;
        }
    }
}
