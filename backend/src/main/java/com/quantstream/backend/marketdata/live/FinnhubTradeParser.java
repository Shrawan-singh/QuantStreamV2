/*
 * ==================================================================================
 * FILE: FinnhubTradeParser.java
 * ==================================================================================
 *
 * WHAT THIS FILE DOES:
 * This is the "Translator" for incoming data from Finnhub's live WebSocket.
 *
 * THE RAW DATA RECEIVED OVER THE WIRE:
 * Finnhub sends live market messages as raw JSON text over the internet.
 * Here is an example of what Finnhub sends when someone buys Apple stock:
 * {
 *   "type": "trade",
 *   "data": [
 *     {
 *       "s": "AAPL",            // Stock ticker symbol
 *       "p": 235.45,            // Price the trade happened at ($235.45)
 *       "v": 100,               // Number of shares traded (100 shares)
 *       "t": 1727025600000      // Unix timestamp in milliseconds
 *     }
 *   ]
 * }
 *
 * WHAT THIS CLASS DOES:
 * 1. Takes the raw JSON string text.
 * 2. Uses Jackson (a popular Java JSON library) to read the fields: "s", "p", "v", "t".
 * 3. Handles non-trade messages:
 *    - "ping": Just a heartbeat from Finnhub to see if we're still awake. Ignored safely.
 *    - "error": If an invalid API key was supplied, logs a warning.
 * 4. Converts the trade data into our system's standard, strongly-typed `StockTick` record!
 * ==================================================================================
 */

package com.quantstream.backend.marketdata.live;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.quantstream.backend.domain.StockTick;
import com.quantstream.backend.domain.TickEventType;
import com.quantstream.backend.domain.TickSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Parses real-time WebSocket trade messages from the Finnhub API into {@link StockTick} instances.
 */
@Component
public class FinnhubTradeParser {

    private static final Logger logger = LoggerFactory.getLogger(FinnhubTradeParser.class);
    private final ObjectMapper objectMapper; // Jackson JSON parser

    public FinnhubTradeParser() {
        this(new ObjectMapper());
    }

    public FinnhubTradeParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
    }

    /**
     * Parses a Finnhub JSON payload into a list of {@link StockTick} records.
     *
     * @param payload raw text message received from Finnhub WebSocket
     * @return list of parsed StockTicks, or empty list if the message is a ping, error, or empty
     */
    public List<StockTick> parse(String payload) {
        if (payload == null || payload.isBlank()) {
            return Collections.emptyList();
        }

        try {
            // Read JSON text into a tree of nodes
            JsonNode root = objectMapper.readTree(payload);
            String type = root.path("type").asText("");

            // Filter out heartbeats
            if ("ping".equalsIgnoreCase(type)) {
                logger.trace("Received Finnhub ping frame");
                return Collections.emptyList();
            }

            // Handle API errors
            if ("error".equalsIgnoreCase(type)) {
                String errorMsg = root.path("msg").asText("Unknown Finnhub error");
                logger.warn("Received error from Finnhub WebSocket: {}", errorMsg);
                return Collections.emptyList();
            }

            // If it's not a trade message, ignore it
            if (!"trade".equalsIgnoreCase(type)) {
                logger.debug("Ignoring non-trade Finnhub message type: {}", type);
                return Collections.emptyList();
            }

            // Extract the "data" array of trades
            JsonNode dataArray = root.path("data");
            if (!dataArray.isArray() || dataArray.isEmpty()) {
                return Collections.emptyList();
            }

            // Loop through each trade inside the data array and parse it
            List<StockTick> ticks = new ArrayList<>(dataArray.size());
            for (JsonNode tradeNode : dataArray) {
                StockTick tick = parseSingleTrade(tradeNode);
                if (tick != null) {
                    ticks.add(tick);
                }
            }

            return ticks;
        } catch (Exception e) {
            logger.warn("Failed to parse Finnhub WebSocket message: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Helper to parse a single trade item inside the "data" array:
     * {"s":"AAPL", "p":235.45, "v":100, "t":1727025600000}
     */
    private StockTick parseSingleTrade(JsonNode node) {
        try {
            String symbol = node.path("s").asText(null);
            if (symbol == null || symbol.isBlank()) {
                return null;
            }

            double rawPrice = node.path("p").asDouble(0.0);
            if (rawPrice <= 0.0) {
                return null;
            }
            // Format price to 2 decimal places using BigDecimal
            BigDecimal price = BigDecimal.valueOf(rawPrice).setScale(2, RoundingMode.HALF_UP);

            double rawVolume = node.path("v").asDouble(0.0);
            long volume = Math.max(1L, Math.round(rawVolume));

            // Convert epoch millisecond timestamp to Java Instant
            long timestampMs = node.path("t").asLong(0L);
            Instant timestamp = timestampMs > 0 ? Instant.ofEpochMilli(timestampMs) : Instant.now();

            // Construct immutable StockTick marked with LIVE_PROVIDER source
            return new StockTick(
                    UUID.randomUUID(),
                    symbol.trim().toUpperCase(),
                    price,
                    volume,
                    timestamp,
                    TickEventType.TRADE,
                    TickSource.LIVE_PROVIDER,
                    "US"
            );
        } catch (Exception e) {
            logger.debug("Error parsing single Finnhub trade node: {}", e.getMessage());
            return null;
        }
    }
}
