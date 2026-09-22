/*
 * ==================================================================================
 * FILE: AnalyticsController.java
 * ==================================================================================
 *
 * WHAT THIS FILE DOES:
 * This is the REST API Controller for Quantitative Analytics, Scanners, and Market Breadth.
 *
 * ENDPOINTS PROVIDED:
 * 1. `GET /api/analytics/{symbol}` (e.g. `/api/analytics/AAPL`):
 *    Retrieves the full explainable Conviction Score breakdown and factor scores
 *    for an individual stock.
 *
 * 2. `GET /api/analytics/scanner?limit=10&category=VERY_STRONG`:
 *    The "Stock Screener / Leaderboard"!
 *    Ranks all stocks across the entire market by their Conviction Score in descending
 *    order (highest scoring stocks first). Users can filter by category (e.g. only "STRONG" stocks).
 *
 * 3. `GET /api/analytics/summary`:
 *    "Market Breadth" / Market Overview:
 *    Calculates how many stocks are advancing (in green) vs declining (in red),
 *    and the average overall Conviction Score of the entire market today!
 * ==================================================================================
 */

package com.quantstream.backend.controller;

import com.quantstream.backend.analytics.AnalyticsEngine;
import com.quantstream.backend.domain.dto.AnalyticsSnapshot;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/analytics")
public class AnalyticsController {

    private final AnalyticsEngine analyticsEngine;

    public AnalyticsController(AnalyticsEngine analyticsEngine) {
        this.analyticsEngine = analyticsEngine;
    }

    /**
     * Retrieves analytics snapshot and explainable score for a single symbol.
     * Route: GET /api/analytics/{symbol}
     */
    @GetMapping("/{symbol}")
    public ResponseEntity<AnalyticsSnapshot> getAnalytics(@PathVariable String symbol) {
        return analyticsEngine.getLatestSnapshot(symbol)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Quantitative scanner ranking stocks by conviction score descending.
     * Route: GET /api/analytics/scanner?limit=10&category=STRONG
     */
    @GetMapping("/scanner")
    public ResponseEntity<List<AnalyticsSnapshot>> getScanner(
            @RequestParam(defaultValue = "10") int limit,
            @RequestParam(required = false) String category
    ) {
        List<AnalyticsSnapshot> ranked = analyticsEngine.getTopScoringStocks(limit * 2);

        // Filter by category if user provided one (e.g. VERY_STRONG, STRONG, etc.)
        if (category != null && !category.isBlank()) {
            ranked = ranked.stream()
                    .filter(s -> s.scoreCategory() != null && s.scoreCategory().name().equalsIgnoreCase(category.trim()))
                    .toList();
        }

        if (ranked.size() > limit) {
            ranked = ranked.subList(0, limit);
        }

        return ResponseEntity.ok(ranked);
    }

    /**
     * High-level market breadth and conviction summary (advancers, decliners, avg score).
     * Route: GET /api/analytics/summary
     */
    @GetMapping("/summary")
    public ResponseEntity<Map<String, Object>> getSummary() {
        List<AnalyticsSnapshot> all = analyticsEngine.getAllLatestSnapshots();

        // Count how many stocks are moving up vs moving down
        long advancing = all.stream().filter(s -> s.priceChangePercent() > 0).count();
        long declining = all.stream().filter(s -> s.priceChangePercent() < 0).count();
        long neutral = all.stream().filter(s -> s.priceChangePercent() == 0).count();

        // Calculate market-wide average conviction score
        double sum = 0.0;
        for (AnalyticsSnapshot s : all) {
            sum += s.convictionScore();
        }
        double avgScore = all.isEmpty() ? 50.0 : (sum / all.size());

        Map<String, Object> summary = new HashMap<>();
        summary.put("trackedCount", all.size());
        summary.put("advancingCount", advancing);
        summary.put("decliningCount", declining);
        summary.put("neutralCount", neutral);
        summary.put("averageConvictionScore", Math.round(avgScore * 10.0) / 10.0);

        return ResponseEntity.ok(summary);
    }
}
