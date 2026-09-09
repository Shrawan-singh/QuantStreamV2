package com.quantstream.backend.marketdata.live;

import com.quantstream.backend.domain.StockTick;
import com.quantstream.backend.domain.TickEventType;
import com.quantstream.backend.domain.TickSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FinnhubTradeParserTest {

    private FinnhubTradeParser parser;

    @BeforeEach
    void setUp() {
        parser = new FinnhubTradeParser();
    }

    @Test
    void testParseSingleTrade() {
        String json = """
                {
                  "type": "trade",
                  "data": [
                    {
                      "s": "AAPL",
                      "p": 178.52,
                      "v": 150,
                      "t": 1698765432000,
                      "c": ["1", "12"]
                    }
                  ]
                }
                """;

        List<StockTick> ticks = parser.parse(json);

        assertEquals(1, ticks.size());
        StockTick tick = ticks.get(0);
        assertEquals("AAPL", tick.symbol());
        assertEquals(new BigDecimal("178.52"), tick.price());
        assertEquals(150L, tick.volume());
        assertEquals(Instant.ofEpochMilli(1698765432000L), tick.timestamp());
        assertEquals(TickEventType.TRADE, tick.eventType());
        assertEquals(TickSource.LIVE_PROVIDER, tick.source());
        assertEquals("US", tick.exchange());
        assertNotNull(tick.id());
    }

    @Test
    void testParseMultipleTradesBatch() {
        String json = """
                {
                  "type": "trade",
                  "data": [
                    {
                      "s": "MSFT",
                      "p": 330.25,
                      "v": 200,
                      "t": 1698765432100
                    },
                    {
                      "s": "NVDA",
                      "p": 450.75,
                      "v": 80,
                      "t": 1698765432200
                    },
                    {
                      "s": "AMZN",
                      "p": 135.10,
                      "v": 500,
                      "t": 1698765432300
                    }
                  ]
                }
                """;

        List<StockTick> ticks = parser.parse(json);

        assertEquals(3, ticks.size());
        assertEquals("MSFT", ticks.get(0).symbol());
        assertEquals(new BigDecimal("330.25"), ticks.get(0).price());

        assertEquals("NVDA", ticks.get(1).symbol());
        assertEquals(new BigDecimal("450.75"), ticks.get(1).price());

        assertEquals("AMZN", ticks.get(2).symbol());
        assertEquals(new BigDecimal("135.10"), ticks.get(2).price());
    }

    @Test
    void testParsePingFrame_ReturnsEmpty() {
        String json = "{\"type\":\"ping\"}";
        List<StockTick> ticks = parser.parse(json);
        assertTrue(ticks.isEmpty());
    }

    @Test
    void testParseErrorFrame_ReturnsEmpty() {
        String json = "{\"type\":\"error\",\"msg\":\"Invalid API key\"}";
        List<StockTick> ticks = parser.parse(json);
        assertTrue(ticks.isEmpty());
    }

    @Test
    void testParseEmptyOrNullPayload() {
        assertTrue(parser.parse(null).isEmpty());
        assertTrue(parser.parse("").isEmpty());
        assertTrue(parser.parse("   ").isEmpty());
        assertTrue(parser.parse("{ invalid json }").isEmpty());
    }

    @Test
    void testParseTradeWithZeroPrice_Ignored() {
        String json = """
                {
                  "type": "trade",
                  "data": [
                    {
                      "s": "AAPL",
                      "p": 0.0,
                      "v": 100,
                      "t": 1698765432000
                    }
                  ]
                }
                """;

        List<StockTick> ticks = parser.parse(json);
        assertTrue(ticks.isEmpty(), "Zero price trades should be filtered out");
    }

    @Test
    void testParseFractionalVolume_RoundsAtLeastToOne() {
        String json = """
                {
                  "type": "trade",
                  "data": [
                    {
                      "s": "TSLA",
                      "p": 215.40,
                      "v": 0.05,
                      "t": 1698765432000
                    }
                  ]
                }
                """;

        List<StockTick> ticks = parser.parse(json);
        assertEquals(1, ticks.size());
        assertTrue(ticks.get(0).volume() >= 1L, "Volume should be at least 1");
    }
}
