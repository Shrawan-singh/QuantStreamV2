package com.quantstream.backend.marketdata.simulation;

import com.quantstream.backend.domain.StockTick;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class DeterministicTickGeneratorTest {

    @Test
    void sameSeedProducesSameTickSequence() {
        DeterministicTickGenerator first = new DeterministicTickGenerator(12345L, List.of("RELIANCE", "TCS"));
        DeterministicTickGenerator second = new DeterministicTickGenerator(12345L, List.of("RELIANCE", "TCS"));

        StockTick firstTickA = first.nextTick("RELIANCE");
        StockTick firstTickB = second.nextTick("RELIANCE");

        assertEquals(firstTickA.symbol(), firstTickB.symbol());
        assertEquals(firstTickA.price(), firstTickB.price());
        assertEquals(firstTickA.volume(), firstTickB.volume());
        assertEquals(firstTickA.eventType(), firstTickB.eventType());
        assertEquals(firstTickA.source(), firstTickB.source());
        assertNotNull(firstTickA.timestamp());
        assertNotNull(firstTickB.timestamp());
    }
}
