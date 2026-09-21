package com.quantstream.backend.controller;

import com.quantstream.backend.domain.Instrument;
import com.quantstream.backend.domain.InstrumentRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/instruments")
public class InstrumentController {

    private final String marketMode;

    public InstrumentController(@Value("${quantstream.marketdata.mode:simulation}") String marketMode) {
        this.marketMode = marketMode;
    }

    /**
     * Returns all supported instruments (both NSE simulation equities and US live equities).
     */
    @GetMapping
    public ResponseEntity<List<Instrument>> getAllInstruments() {
        return ResponseEntity.ok(InstrumentRegistry.getAllSupportedInstruments());
    }

    /**
     * Returns only the instruments belonging to the currently active market mode.
     */
    @GetMapping("/active")
    public ResponseEntity<List<Instrument>> getActiveInstruments() {
        return ResponseEntity.ok(InstrumentRegistry.getActiveUniverse(marketMode));
    }

    /**
     * Returns the simulation universe instruments.
     */
    @GetMapping("/simulation")
    public ResponseEntity<List<Instrument>> getSimulationInstruments() {
        return ResponseEntity.ok(InstrumentRegistry.getSimulationUniverse());
    }

    /**
     * Returns the curated subset of bellwether instruments for the dashboard overview.
     */
    @GetMapping("/curated")
    public ResponseEntity<List<Instrument>> getCuratedInstruments() {
        return ResponseEntity.ok(InstrumentRegistry.getCuratedDashboardSubset());
    }

    /**
     * Looks up an instrument by symbol.
     */
    @GetMapping("/{symbol}")
    public ResponseEntity<Instrument> getInstrument(@PathVariable String symbol) {
        return InstrumentRegistry.getInstrument(symbol)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
