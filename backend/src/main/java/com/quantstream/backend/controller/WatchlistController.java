/*
 * ==================================================================================
 * FILE: WatchlistController.java
 * ==================================================================================
 *
 * WHAT THIS FILE DOES:
 * This is the REST API Controller for managing the user's personal WATCHLIST.
 *
 * Think of it like a "Favorites / Bookmarks" list:
 *   - `GET /api/watchlist`: Returns the list of pinned stocks, automatically enriched
 *     with their latest live prices and conviction scores from `AnalyticsEngine`.
 *   - `POST /api/watchlist`: Adds a stock to the user's watchlist with optional notes
 *     (e.g. "Looking to buy after earnings").
 *   - `DELETE /api/watchlist/{symbol}`: Unpins and removes a stock from the watchlist.
 *
 * UNIVERSE AWARENESS:
 * When in "live" mode, the user can only add stocks from the 50 US equities.
 * When in "simulation" mode, the user can only add stocks from the ~240 NSE equities.
 * ==================================================================================
 */

package com.quantstream.backend.controller;

import com.quantstream.backend.analytics.AnalyticsEngine;
import com.quantstream.backend.domain.InstrumentRegistry;
import com.quantstream.backend.domain.dto.AnalyticsSnapshot;
import com.quantstream.backend.domain.entity.WatchlistItemEntity;
import com.quantstream.backend.repository.WatchlistRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/watchlist")
public class WatchlistController {

    private final WatchlistRepository watchlistRepository;
    private final AnalyticsEngine analyticsEngine;
    private final String marketMode;

    public WatchlistController(
            WatchlistRepository watchlistRepository,
            AnalyticsEngine analyticsEngine,
            @Value("${quantstream.marketdata.mode:simulation}") String marketMode
    ) {
        this.watchlistRepository = watchlistRepository;
        this.analyticsEngine = analyticsEngine;
        this.marketMode = marketMode;
    }

    /**
     * Returns all watchlist items with their current real-time analytics merged in.
     * Route: GET /api/watchlist
     */
    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> getWatchlist() {
        List<WatchlistItemEntity> items = watchlistRepository.findAll();
        List<Map<String, Object>> response = new ArrayList<>();

        for (WatchlistItemEntity item : items) {
            Map<String, Object> map = new HashMap<>();
            map.put("id", item.getId());
            map.put("symbol", item.getSymbol());
            map.put("companyName", InstrumentRegistry.getCompanyName(item.getSymbol()));
            map.put("notes", item.getNotes());
            map.put("addedAt", item.getAddedAt());

            // Merge current live analytics snapshot (latest price, conviction score, etc.)
            AnalyticsSnapshot snapshot = analyticsEngine.getLatestSnapshot(item.getSymbol()).orElse(null);
            map.put("analytics", snapshot);

            response.add(map);
        }

        return ResponseEntity.ok(response);
    }

    public record AddWatchlistRequest(String symbol, String notes) {}

    /**
     * Adds an instrument to the user watchlist after strict universe validation.
     * Route: POST /api/watchlist
     */
    @PostMapping
    public ResponseEntity<?> addToWatchlist(@RequestBody AddWatchlistRequest request) {
        if (request == null || request.symbol() == null || request.symbol().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Symbol is required"));
        }

        String symbol = request.symbol().trim().toUpperCase();

        // Validate strictly against the active mode's instrument universe
        if (!InstrumentRegistry.isSupportedInCurrentMode(symbol, marketMode)) {
            String modeName = "live".equalsIgnoreCase(marketMode) ? "live (US equities)" : "simulation (NSE equities)";
            return ResponseEntity.badRequest().body(Map.of("error",
                    "Symbol '" + symbol + "' is not available in the current " + modeName + " mode universe."));
        }

        // Prevent duplicate entries
        if (watchlistRepository.existsBySymbol(symbol)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Symbol already in watchlist"));
        }

        String notes = request.notes() != null ? request.notes().trim() : "";
        WatchlistItemEntity entity = new WatchlistItemEntity(symbol, notes);
        WatchlistItemEntity saved = watchlistRepository.save(entity);
        return ResponseEntity.ok(saved);
    }

    /**
     * Removes an instrument from the user watchlist.
     * Route: DELETE /api/watchlist/{symbol}
     */
    @DeleteMapping("/{symbol}")
    @Transactional
    public ResponseEntity<?> removeFromWatchlist(@PathVariable String symbol) {
        if (symbol == null || symbol.isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        String sym = symbol.trim().toUpperCase();
        if (!watchlistRepository.existsBySymbol(sym)) {
            return ResponseEntity.notFound().build();
        }

        watchlistRepository.deleteBySymbol(sym);
        return ResponseEntity.noContent().build();
    }
}
