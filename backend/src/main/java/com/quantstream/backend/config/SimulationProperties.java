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

    @Min(1)
    private long seed = 20260902L;

    @Min(100)
    private long intervalMs = 500L;

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
