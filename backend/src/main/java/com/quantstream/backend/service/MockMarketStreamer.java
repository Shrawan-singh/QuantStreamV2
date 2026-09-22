/*
 * ==================================================================================
 * FILE: MockMarketStreamer.java
 * ==================================================================================
 *
 * WHAT THIS FILE DOES:
 * This is the "IGNITION SWITCH" for market data streaming in QuantStream.
 *
 * WHEN THE APPLICATION BOOTS UP:
 * 1. Checks whether we are in "simulation" or "live" mode:
 *    - In "live" mode: Collects the 50 US stocks from `InstrumentRegistry.getLiveSymbols()`.
 *    - In "simulation" mode: Collects the ~240 Indian stocks from simulation properties.
 * 2. Connects the data wire:
 *    `marketDataProvider.setTickListener(tickPublisher::publish)`
 *    (Whenever the provider gets a tick, immediately send it to the TickPublisher!)
 * 3. Subscribes to all the chosen stocks.
 * 4. Calls `marketDataProvider.connect()` to turn on the faucet of prices!
 *
 * ON SHUTDOWN:
 * When the server is stopped, calls `disconnect()` to cleanly turn off the stream.
 * ==================================================================================
 */

package com.quantstream.backend.service;

import com.quantstream.backend.config.SimulationProperties;
import com.quantstream.backend.marketdata.MarketDataProvider;
import com.quantstream.backend.messaging.TickPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import jakarta.annotation.PreDestroy;
import java.util.List;

@Service
public class MockMarketStreamer {

    private static final Logger logger = LoggerFactory.getLogger(MockMarketStreamer.class);

    private final MarketDataProvider marketDataProvider;
    private final TickPublisher tickPublisher;
    private final SimulationProperties simulationProperties;

    // "simulation" or "live"
    @org.springframework.beans.factory.annotation.Value("${quantstream.marketdata.mode:simulation}")
    private String marketDataMode;

    // Optional override list of symbols from config
    @org.springframework.beans.factory.annotation.Value("${quantstream.marketdata.symbols:}")
    private List<String> liveSymbols;

    private volatile boolean started;

    public MockMarketStreamer(MarketDataProvider marketDataProvider,
                              TickPublisher tickPublisher,
                              SimulationProperties simulationProperties) {
        this.marketDataProvider = marketDataProvider;
        this.tickPublisher = tickPublisher;
        this.simulationProperties = simulationProperties;
    }

    /**
     * Ignition method: connects the data provider to the publisher and begins streaming.
     */
    public synchronized void start() {
        if (started) {
            return; // Already streaming
        }

        boolean isLive = "live".equalsIgnoreCase(marketDataMode);
        if (!isLive && !simulationProperties.isAutoStart()) {
            return;
        }

        // Connect the pipeline: Data Provider -> Tick Publisher
        marketDataProvider.setTickListener(tickPublisher::publish);

        // Determine which stock universe to subscribe to based on active mode
        List<String> symbolsToSubscribe;
        if ("live".equalsIgnoreCase(marketDataMode)) {
            symbolsToSubscribe = (liveSymbols != null && !liveSymbols.isEmpty())
                    ? liveSymbols
                    : com.quantstream.backend.domain.InstrumentRegistry.getLiveSymbols();
        } else {
            symbolsToSubscribe = simulationProperties.getSymbols();
        }

        // Subscribe to every symbol in the universe
        symbolsToSubscribe.forEach(marketDataProvider::subscribe);

        // Turn on the market connection!
        marketDataProvider.connect();
        started = true;

        logger.info("Market data streamer started in {} mode for symbols: {}",
                marketDataMode.toUpperCase(), symbolsToSubscribe);
    }

    /**
     * Clean shutdown on application exit.
     */
    @PreDestroy
    public synchronized void stop() {
        if (!started) {
            return;
        }

        marketDataProvider.disconnect();
        started = false;
        logger.info("Simulation stopped");
    }
}
