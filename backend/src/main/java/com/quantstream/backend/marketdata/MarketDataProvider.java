package com.quantstream.backend.marketdata;

import com.quantstream.backend.domain.StockTick;

import java.util.function.Consumer;

public interface MarketDataProvider {

    void connect();

    void subscribe(String symbol);

    void unsubscribe(String symbol);

    void disconnect();

    String healthStatus();

    void setTickListener(Consumer<StockTick> tickListener);
}
