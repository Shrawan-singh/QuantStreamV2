/*
 * ==================================================================================
 * FILE: DeterministicTickGenerator.java
 * ==================================================================================
 *
 * WHAT THIS FILE DOES:
 * When running in SIMULATION mode, we don't have real live prices from an exchange.
 * This class acts as a synthetic "Market Simulator" that generates realistic,
 * lifelike stock price ticks using mathematics (a random walk with volatility).
 *
 * WHAT DOES "DETERMINISTIC" MEAN?
 * In computer science, "deterministic" means: "If you give the generator the same
 * starting seed number (e.g. seed = 42), it will produce the EXACT SAME sequence
 * of prices every single time!"
 * Why is this awesome?
 * Because unit tests and debugging become 100% reproducible! If a bug happens on
 * tick #57, you can re-run the program and tick #57 will have the exact same price.
 *
 * HOW IT CALCULATES THE NEXT PRICE:
 * 1. Takes the previous price of the stock.
 * 2. Picks a small random step bounded by realistic ~0.3% max volatility:
 *      delta = (random - 0.495) * (price * 0.003)
 *      (Note: 0.495 instead of 0.500 gives the market a very tiny realistic upward drift).
 * 3. Adds delta to previous price, ensuring the stock price never drops below $1.00.
 * 4. Generates a random trade volume between 10,000 and 160,000 shares.
 * 5. Returns a shiny new StockTick record!
 * ==================================================================================
 */

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

    // Pseudo-random number generator initialized with a repeatable seed
    private final Random random;

    // Remembers the last simulated price for each symbol so price changes are continuous
    private final Map<String, BigDecimal> lastPrices = new LinkedHashMap<>();

    // Counter tracking how many ticks have been produced for each symbol
    private final Map<String, Integer> symbolCounters = new LinkedHashMap<>();

    public DeterministicTickGenerator(long seed, List<String> symbols) {
        this.random = new Random(seed);
        for (String symbol : symbols) {
            // Look up base reference price from the InstrumentRegistry (e.g. RELIANCE = 2880.00)
            BigDecimal base = InstrumentRegistry.getBasePrice(symbol);
            lastPrices.put(symbol, base);
            symbolCounters.put(symbol, 0);
        }
    }

    /**
     * Generates the next synthetic price tick for a given symbol.
     * Synchronized so multiple simulation threads don't collide when updating the price map.
     */
    public synchronized StockTick nextTick(String symbol) {
        // Look up previous price, or use baseline price if symbol is seen for first time
        BigDecimal previousPrice = lastPrices.computeIfAbsent(symbol, InstrumentRegistry::getBasePrice);

        // Realistic ~0.3% maximum price wiggle per tick
        double volatilityPercent = 0.003;
        double baseDouble = previousPrice.doubleValue();

        // Random delta calculation: slight upward bias (0.495) to mimic long-term equity growth
        double rawDelta = (random.nextDouble() - 0.495d) * (baseDouble * volatilityPercent);
        BigDecimal delta = BigDecimal.valueOf(rawDelta).setScale(2, RoundingMode.HALF_UP);

        // Ensure price never falls below 1.00 currency unit
        BigDecimal currentPrice = previousPrice.add(delta).max(BigDecimal.valueOf(1.00d)).setScale(2, RoundingMode.HALF_UP);

        // Update memory with new price and increment tick count
        lastPrices.put(symbol, currentPrice);
        symbolCounters.put(symbol, symbolCounters.getOrDefault(symbol, 0) + 1);

        // Generate realistic trade volume (10,000 to 160,000 shares)
        long volume = 10_000L + random.nextInt(150_000);
        Instant timestamp = Instant.now();

        // Package into a complete StockTick
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
