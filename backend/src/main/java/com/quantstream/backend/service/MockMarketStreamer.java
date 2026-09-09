package com.quantstream.backend.service;

import com.quantstream.backend.config.SimulationProperties;
import com.quantstream.backend.marketdata.MarketDataProvider;
import com.quantstream.backend.messaging.StockTickKafkaProducer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import jakarta.annotation.PreDestroy;
import java.util.List;

@Service
public class MockMarketStreamer {

    private static final Logger logger = LoggerFactory.getLogger(MockMarketStreamer.class);

    private final MarketDataProvider marketDataProvider;
    private final StockTickKafkaProducer stockTickKafkaProducer;
    private final SimulationProperties simulationProperties;

    @org.springframework.beans.factory.annotation.Value("${quantstream.marketdata.mode:simulation}")
    private String marketDataMode;

    @org.springframework.beans.factory.annotation.Value("${quantstream.marketdata.symbols:AAPL,MSFT,AMZN,NVDA,GOOGL,META,TSLA}")
    private List<String> liveSymbols;

    private volatile boolean started;

    public MockMarketStreamer(MarketDataProvider marketDataProvider,
                              StockTickKafkaProducer stockTickKafkaProducer,
                              SimulationProperties simulationProperties) {
        this.marketDataProvider = marketDataProvider;
        this.stockTickKafkaProducer = stockTickKafkaProducer;
        this.simulationProperties = simulationProperties;
    }

    public synchronized void start() {
        if (started || !simulationProperties.isAutoStart()) {
            return;
        }

        marketDataProvider.setTickListener(stockTickKafkaProducer::publish);

        List<String> symbolsToSubscribe;
        if ("live".equalsIgnoreCase(marketDataMode)) {
            symbolsToSubscribe = (liveSymbols != null && !liveSymbols.isEmpty())
                    ? liveSymbols
                    : List.of("AAPL", "MSFT", "AMZN", "NVDA", "GOOGL", "META", "TSLA");
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
