package com.quantstream.backend.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class InstrumentRegistryTest {

    @Test
    @DisplayName("Simulation universe count is approximately 240 (exactly 233 validated unique symbols)")
    void simulationUniverseCount() {
        List<Instrument> simulationUniverse = InstrumentRegistry.getSimulationUniverse();
        assertNotNull(simulationUniverse);
        assertEquals(233, simulationUniverse.size(),
                "Expected 233 validated unique simulation instruments (40 existing + 193 validated candidate instruments)");
    }

    @Test
    @DisplayName("Simulation symbols are unique")
    void simulationSymbolsAreUnique() {
        List<String> symbols = InstrumentRegistry.getSimulationSymbols();
        Set<String> unique = new HashSet<>(symbols);
        assertEquals(symbols.size(), unique.size(), "Duplicate simulation symbols detected");
    }

    @Test
    @DisplayName("Live symbols are unique and count is 50")
    void liveSymbolsAreUnique() {
        List<String> liveSymbols = InstrumentRegistry.getLiveSymbols();
        assertEquals(50, liveSymbols.size());
        Set<String> unique = new HashSet<>(liveSymbols);
        assertEquals(liveSymbols.size(), unique.size(), "Duplicate live symbols detected");
        assertTrue(liveSymbols.containsAll(List.of("AAPL", "MSFT", "AMZN", "NVDA", "GOOGL", "META", "TSLA", "AVGO", "JPM", "LLY")));
    }

    @Test
    @DisplayName("No symbol appears in both simulation and live universes")
    void universesAreDisjoint() {
        Set<String> simSymbols = new HashSet<>(InstrumentRegistry.getSimulationSymbols());
        Set<String> liveSymbols = new HashSet<>(InstrumentRegistry.getLiveSymbols());

        Set<String> intersection = new HashSet<>(simSymbols);
        intersection.retainAll(liveSymbols);

        assertTrue(intersection.isEmpty(), "Simulation and Live universes must be disjoint: " + intersection);
    }

    @Test
    @DisplayName("All simulation instruments have NSE exchange and INR currency metadata")
    void simulationMetadataValidation() {
        List<Instrument> simulationUniverse = InstrumentRegistry.getSimulationUniverse();
        for (Instrument inst : simulationUniverse) {
            assertEquals("NSE", inst.exchange(), "Exchange must be NSE for " + inst.symbol());
            assertEquals("INR", inst.currency(), "Currency must be INR for " + inst.symbol());
            assertNotNull(inst.sector(), "Sector must not be null for " + inst.symbol());
            assertFalse(inst.sector().isBlank(), "Sector must not be blank for " + inst.symbol());
            assertNotNull(inst.basePrice(), "Base price must not be null for " + inst.symbol());
            assertTrue(inst.basePrice().compareTo(BigDecimal.ZERO) > 0, "Base price must be positive for " + inst.symbol());
        }
    }

    @Test
    @DisplayName("Invalid and unverified candidate symbols are not loaded")
    void invalidSymbolsAreNotSupported() {
        // Excluded due to symbol mismatch or renamed status on official NSE list
        List<String> unverified = List.of(
                "IPCA",         // Official is IPCALAB
                "CEAT",         // Official is CEATLTD
                "GUJGASLTD",    // Renamed to GUJENERGY on 01-JUL-2026
                "BIRLASOFT",    // Official is BSOFT
                "AJANTAPHARM",  // Official is AJANTPHARM
                "RANDOM_XYZ",
                "FAKE_TICKER"
        );

        for (String sym : unverified) {
            assertFalse(InstrumentRegistry.isSupported(sym), "Unverified symbol must not be supported: " + sym);
            assertFalse(InstrumentRegistry.isSimulationSupported(sym), "Unverified symbol must not be in simulation: " + sym);
            assertEquals(Optional.empty(), InstrumentRegistry.getInstrument(sym));
        }
    }

    @Test
    @DisplayName("Curated dashboard subset contains 8 bellwethers all present in simulation universe")
    void curatedDashboardSubsetValidation() {
        List<Instrument> curated = InstrumentRegistry.getCuratedDashboardSubset();
        assertEquals(8, curated.size());
        for (Instrument inst : curated) {
            assertTrue(InstrumentRegistry.isSimulationSupported(inst.symbol()),
                    "Curated symbol must be in simulation universe: " + inst.symbol());
        }
    }

    @Test
    @DisplayName("Base prices serve as simulation initialization data and exist for all supported symbols")
    void basePricesAvailable() {
        for (String sym : InstrumentRegistry.getSimulationSymbols()) {
            BigDecimal base = InstrumentRegistry.getBasePrice(sym);
            assertNotNull(base);
            assertTrue(base.compareTo(BigDecimal.ZERO) > 0);
        }
    }
}
