package com.quantstream.backend.messaging;

import com.quantstream.backend.domain.StockTick;
import com.quantstream.backend.domain.TickEventType;
import com.quantstream.backend.domain.TickSource;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class KafkaSerializationTest {

    @Test
    void stockTickRoundTripsThroughJsonSerializer() {
        StockTick tick = new StockTick(
                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                "TCS",
                BigDecimal.valueOf(4250.75),
                250_000L,
                Instant.parse("2026-09-02T10:15:30Z"),
                TickEventType.TRADE,
                TickSource.SIMULATION,
                "NSE"
        );

        try (JsonSerializer<StockTick> serializer = new JsonSerializer<>();
             JsonDeserializer<StockTick> deserializer = new JsonDeserializer<>(StockTick.class)) {
            deserializer.addTrustedPackages("com.quantstream.backend.domain");

            byte[] payload = serializer.serialize("market-ticks", new RecordHeaders(), tick);
            StockTick roundTripped = deserializer.deserialize("market-ticks", new RecordHeaders(), payload);

            assertEquals(tick, roundTripped);
        }
    }
}