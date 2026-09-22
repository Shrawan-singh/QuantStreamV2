/*
 * ==================================================================================
 * FILE: MarketDataProvider.java
 * ==================================================================================
 *
 * WHAT THIS FILE DOES:
 * This is the common "Interface" (contract) for any market data source in QuantStream.
 *
 * REAL WORLD ANALOGY:
 * Think of this like a universal power outlet or a generic radio tuner. Whether the
 * music comes from an FM radio station, an internet podcast, or a cassette tape,
 * the stereo system only cares about standard controls:
 *   - Turn on:        connect()
 *   - Pick a station: subscribe("AAPL")
 *   - Leave station:  unsubscribe("AAPL")
 *   - Turn off:       disconnect()
 *   - Listen to song: setTickListener(song -> play(song))
 *
 * TWO IMPLEMENTATIONS IN QUANTSTREAM:
 * 1. "MockMarketDataProvider":
 *    Generates simulated synthetic prices using math for ~240 Indian stocks.
 * 2. "LiveMarketDataProvider":
 *    Connects to the real Finnhub WebSocket to stream real US market trades.
 *
 * Because both implement this same interface, the rest of the application doesn't
 * care where the ticks come from — it handles them the exact same way!
 * ==================================================================================
 */

package com.quantstream.backend.marketdata;

import com.quantstream.backend.domain.StockTick;

import java.util.function.Consumer;

/**
 * Common abstraction for all market data sources (simulation or live exchange streams).
 */
public interface MarketDataProvider {

    /**
     * Opens the connection to the data source (e.g. starts the simulation clock or connects to WebSocket).
     */
    void connect();

    /**
     * Starts listening for prices of a specific stock ticker (e.g. "AAPL").
     */
    void subscribe(String symbol);

    /**
     * Stops listening for prices of a specific stock ticker.
     */
    void unsubscribe(String symbol);

    /**
     * Closes the connection and releases network/timer resources.
     */
    void disconnect();

    /**
     * Returns a string describing connection health (e.g. "CONNECTED", "DISCONNECTED", "UNCONFIGURED").
     */
    String healthStatus();

    /**
     * Registers a callback function (listener) that will be invoked every time a new price tick arrives.
     *
     * @param tickListener the consumer function that receives each StockTick
     */
    void setTickListener(Consumer<StockTick> tickListener);
}
