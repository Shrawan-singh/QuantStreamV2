/*
 * ==================================================================================
 * FILE: SimulationProperties.java
 * ==================================================================================
 *
 * WHAT THIS FILE DOES:
 * Holds settings for the synthetic market data generator.
 *
 * Spring automatically binds this to the YAML section: `quantstream.simulation`.
 *
 * SETTINGS:
 * - autoStart:   Whether the simulation starts immediately on app launch (default: true).
 * - seed:        The random number seed for 100% reproducible price sequences (default: 20260902).
 * - intervalMs:  How often ticks are generated in milliseconds (e.g. 500ms = 2 ticks/sec).
 * - symbols:     The list of simulation stocks (defaults to ~240 Indian equities).
 * ==================================================================================
 */

package com.quantstream.backend.config;

import com.quantstream.backend.domain.InstrumentRegistry;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.ArrayList;
import java.util.List;

@Validated
@ConfigurationProperties(prefix = "quantstream.simulation")
public class SimulationProperties {

    private boolean autoStart = true;

    // Seed for reproducible pseudo-random walk
    @Min(1)
    private long seed = 20260902L;

    // Time gap between simulated tick batches (in milliseconds)
    @Min(100)
    private long intervalMs = 500L;

    // Symbols to simulate (defaults to the full ~240 NSE universe)
    @NotEmpty
    private List<String> symbols = new ArrayList<>(InstrumentRegistry.getSimulationSymbols());

    public boolean isAutoStart() {
        return autoStart;
    }

    public void setAutoStart(boolean autoStart) {
        this.autoStart = autoStart;
    }

    public long getSeed() {
        return seed;
    }

    public void setSeed(long seed) {
        this.seed = seed;
    }

    public long getIntervalMs() {
        return intervalMs;
    }

    public void setIntervalMs(long intervalMs) {
        this.intervalMs = intervalMs;
    }

    public List<String> getSymbols() {
        if (symbols == null || symbols.isEmpty()) {
            return InstrumentRegistry.getSimulationSymbols();
        }
        return symbols;
    }

    public void setSymbols(List<String> symbols) {
        List<String> filtered = symbols == null ? List.of() : symbols.stream().filter(s -> s != null && !s.isBlank()).toList();
        if (!filtered.isEmpty()) {
            this.symbols = new ArrayList<>(filtered);
        } else {
            this.symbols = new ArrayList<>(InstrumentRegistry.getSimulationSymbols());
        }
    }
}
