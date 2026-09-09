package com.quantstream.backend.marketdata.simulation;

import com.quantstream.backend.domain.StockTick;
import com.quantstream.backend.domain.TickEventType;
import com.quantstream.backend.domain.TickSource;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

public class DeterministicTickGenerator {

    private final Random random;
    private final Map<String, BigDecimal> lastPrices = new LinkedHashMap<>();
    private final Map<String, Integer> symbolCounters = new LinkedHashMap<>();

    public DeterministicTickGenerator(long seed, List<String> symbols) {
        this.random = new Random(seed);
        lastPrices.put("RELIANCE", new BigDecimal("2900.00"));
        lastPrices.put("TCS", new BigDecimal("4250.00"));
        lastPrices.put("INFY", new BigDecimal("1550.00"));
        lastPrices.put("HDFCBANK", new BigDecimal("1710.00"));
        lastPrices.put("ICICIBANK", new BigDecimal("1280.00"));
        symbols.forEach(symbol -> symbolCounters.put(symbol, 0));
    }

    public synchronized StockTick nextTick(String symbol) {
        BigDecimal previousPrice = lastPrices.getOrDefault(symbol, new BigDecimal("100.00"));
        double rawDelta = (random.nextDouble() - 0.5d) * 4.0d;
        BigDecimal delta = BigDecimal.valueOf(rawDelta).setScale(2, RoundingMode.HALF_UP);
        BigDecimal currentPrice = previousPrice.add(delta).max(BigDecimal.valueOf(1.00d)).setScale(2, RoundingMode.HALF_UP);

        lastPrices.put(symbol, currentPrice);
        symbolCounters.put(symbol, symbolCounters.getOrDefault(symbol, 0) + 1);

        long volume = 100_000L + random.nextInt(900_000);
        Instant timestamp = Instant.now();

        return new StockTick(
                UUID.nameUUIDFromBytes((symbol + "-" + symbolCounters.get(symbol)).getBytes()),
                symbol,
                currentPrice,
                volume,
                timestamp,
                TickEventType.TRADE,
                TickSource.SIMULATION,
                "NSE"
        );
    }
}
