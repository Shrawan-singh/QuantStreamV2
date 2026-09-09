package com.quantstream.backend.controller;

import com.quantstream.backend.analytics.AnalyticsEngine;
import com.quantstream.backend.domain.dto.AnalyticsSnapshot;
import com.quantstream.backend.domain.entity.WatchlistItemEntity;
import com.quantstream.backend.repository.WatchlistRepository;
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

    public WatchlistController(WatchlistRepository watchlistRepository, AnalyticsEngine analyticsEngine) {
        this.watchlistRepository = watchlistRepository;
        this.analyticsEngine = analyticsEngine;
    }

    /**
     * Returns all watchlist items with their current real-time analytics.
     */
    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> getWatchlist() {
        List<WatchlistItemEntity> items = watchlistRepository.findAll();
        List<Map<String, Object>> response = new ArrayList<>();

        for (WatchlistItemEntity item : items) {
            Map<String, Object> map = new HashMap<>();
            map.put("id", item.getId());
            map.put("symbol", item.getSymbol());
            map.put("notes", item.getNotes());
            map.put("addedAt", item.getAddedAt());

            AnalyticsSnapshot snapshot = analyticsEngine.getLatestSnapshot(item.getSymbol()).orElse(null);
            map.put("analytics", snapshot);

            response.add(map);
        }

        return ResponseEntity.ok(response);
    }

    public record AddWatchlistRequest(String symbol, String notes) {}

    /**
     * Adds an instrument to the user watchlist.
     */
    @PostMapping
    public ResponseEntity<?> addToWatchlist(@RequestBody AddWatchlistRequest request) {
        if (request == null || request.symbol() == null || request.symbol().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Symbol is required"));
        }

        String symbol = request.symbol().trim().toUpperCase();
        if (watchlistRepository.existsBySymbol(symbol)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Symbol already in watchlist"));
        }

        WatchlistItemEntity entity = new WatchlistItemEntity(symbol, request.notes());
        WatchlistItemEntity saved = watchlistRepository.save(entity);
        return ResponseEntity.ok(saved);
    }

    /**
     * Removes an instrument from the user watchlist.
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
