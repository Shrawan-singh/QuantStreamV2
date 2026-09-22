/*
 * ==================================================================================
 * FILE: InstrumentController.java
 * ==================================================================================
 *
 * WHAT THIS FILE DOES:
 * This is the REST API Controller for querying the master stock directory.
 *
 * ENDPOINTS PROVIDED:
 * - `GET /api/instruments`:
 *   Returns all registered stocks across both universes.
 * - `GET /api/instruments/active`:
 *   Returns only stocks for the CURRENT mode (e.g. 50 US stocks if in live mode;
 *   ~240 Indian stocks if in simulation mode).
 * - `GET /api/instruments/curated`:
 *   Returns a hand-picked subset of famous bellwether stocks for the dashboard homepage.
 * - `GET /api/instruments/{symbol}`:
 *   Looks up detailed metadata (company name, sector, exchange, currency) for a single ticker.
 * ==================================================================================
 */

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
     * Route: GET /api/instruments
     */
    @GetMapping
    public ResponseEntity<List<Instrument>> getAllInstruments() {
        return ResponseEntity.ok(InstrumentRegistry.getAllSupportedInstruments());
    }

    /**
     * Returns only the instruments belonging to the currently active market mode.
     * Route: GET /api/instruments/active
     */
    @GetMapping("/active")
    public ResponseEntity<List<Instrument>> getActiveInstruments() {
        return ResponseEntity.ok(InstrumentRegistry.getActiveUniverse(marketMode));
    }

    /**
     * Returns the simulation universe instruments (~240 NSE equities).
     * Route: GET /api/instruments/simulation
     */
    @GetMapping("/simulation")
    public ResponseEntity<List<Instrument>> getSimulationInstruments() {
        return ResponseEntity.ok(InstrumentRegistry.getSimulationUniverse());
    }

    /**
     * Returns the curated subset of bellwether instruments for the dashboard overview.
     * Route: GET /api/instruments/curated
     */
    @GetMapping("/curated")
    public ResponseEntity<List<Instrument>> getCuratedInstruments() {
        return ResponseEntity.ok(InstrumentRegistry.getCuratedDashboardSubset());
    }

    /**
     * Looks up an instrument by symbol (e.g. /api/instruments/AAPL).
     * Route: GET /api/instruments/{symbol}
     */
    @GetMapping("/{symbol}")
    public ResponseEntity<Instrument> getInstrument(@PathVariable String symbol) {
        return InstrumentRegistry.getInstrument(symbol)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
