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

    @org.springframework.beans.factory.annotation.Value("${quantstream.marketdata.mode:simulation}")
    private String marketDataMode;

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

    public synchronized void start() {
        if (started) {
            return;
        }

        boolean isLive = "live".equalsIgnoreCase(marketDataMode);
        if (!isLive && !simulationProperties.isAutoStart()) {
            return;
        }

        marketDataProvider.setTickListener(tickPublisher::publish);

        List<String> symbolsToSubscribe;
        if ("live".equalsIgnoreCase(marketDataMode)) {
            symbolsToSubscribe = (liveSymbols != null && !liveSymbols.isEmpty())
                    ? liveSymbols
                    : com.quantstream.backend.domain.InstrumentRegistry.getLiveSymbols();
        } else {
            symbolsToSubscribe = simulationProperties.getSymbols();
        }

        symbolsToSubscribe.forEach(marketDataProvider::subscribe);
        marketDataProvider.connect();
        started = true;

        logger.info("Market data streamer started in {} mode for symbols: {}",
                marketDataMode.toUpperCase(), symbolsToSubscribe);
    }

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
