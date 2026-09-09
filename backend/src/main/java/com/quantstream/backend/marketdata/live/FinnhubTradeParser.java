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
 *
 * <p>Finnhub trade message format:
 * <pre>{@code
 * {
 *   "type": "trade",
 *   "data": [
 *     {
 *       "p": 182.52,          // Last price
 *       "s": "AAPL",            // Symbol
 *       "t": 1698765432000,     // Unix timestamp in milliseconds
 *       "v": 100,               // Volume
 *       "c": ["1", "12"]        // Trade conditions (optional)
 *     }
 *   ]
 * }
 * }</pre>
 * </p>
 */
@Component
public class FinnhubTradeParser {

    private static final Logger logger = LoggerFactory.getLogger(FinnhubTradeParser.class);
    private final ObjectMapper objectMapper;

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
            JsonNode root = objectMapper.readTree(payload);
            String type = root.path("type").asText("");

            if ("ping".equalsIgnoreCase(type)) {
                logger.trace("Received Finnhub ping frame");
                return Collections.emptyList();
            }

            if ("error".equalsIgnoreCase(type)) {
                String errorMsg = root.path("msg").asText("Unknown Finnhub error");
                logger.warn("Received error from Finnhub WebSocket: {}", errorMsg);
                return Collections.emptyList();
            }

            if (!"trade".equalsIgnoreCase(type)) {
                logger.debug("Ignoring non-trade Finnhub message type: {}", type);
                return Collections.emptyList();
            }

            JsonNode dataArray = root.path("data");
            if (!dataArray.isArray() || dataArray.isEmpty()) {
                return Collections.emptyList();
            }

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
            BigDecimal price = BigDecimal.valueOf(rawPrice).setScale(2, RoundingMode.HALF_UP);

            double rawVolume = node.path("v").asDouble(0.0);
            long volume = Math.max(1L, Math.round(rawVolume));

            long timestampMs = node.path("t").asLong(0L);
            Instant timestamp = timestampMs > 0 ? Instant.ofEpochMilli(timestampMs) : Instant.now();

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
