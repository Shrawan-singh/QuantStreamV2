package com.quantstream.backend.marketdata.simulation;

import com.quantstream.backend.domain.InstrumentRegistry;
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
        for (String symbol : symbols) {
            BigDecimal base = InstrumentRegistry.getBasePrice(symbol);
            lastPrices.put(symbol, base);
            symbolCounters.put(symbol, 0);
        }
    }

    public synchronized StockTick nextTick(String symbol) {
        BigDecimal previousPrice = lastPrices.computeIfAbsent(symbol, InstrumentRegistry::getBasePrice);
        double volatilityPercent = 0.003; // realistic realistic ~0.3% max step per tick
        double baseDouble = previousPrice.doubleValue();
        double rawDelta = (random.nextDouble() - 0.495d) * (baseDouble * volatilityPercent);
        BigDecimal delta = BigDecimal.valueOf(rawDelta).setScale(2, RoundingMode.HALF_UP);
        BigDecimal currentPrice = previousPrice.add(delta).max(BigDecimal.valueOf(1.00d)).setScale(2, RoundingMode.HALF_UP);

        lastPrices.put(symbol, currentPrice);
        symbolCounters.put(symbol, symbolCounters.getOrDefault(symbol, 0) + 1);

        long volume = 10_000L + random.nextInt(150_000);
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
