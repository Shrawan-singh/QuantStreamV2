/*
 * ==================================================================================
 * FILE: ConfigController.java
 * ==================================================================================
 *
 * WHAT THIS FILE DOES:
 * This is the REST API Controller for inspecting the active application configuration.
 *
 * Route: `GET /api/config`
 *
 * WHAT IT RETURNS:
 * A comprehensive JSON view of all active settings:
 *   - Indicators config: SMA period (20), EMA period (20), RSI period (14), etc.
 *   - Scoring config: Trend weight (25%), Momentum weight (25%), etc.
 *   - Market Data mode: "simulation" or "live", provider name, connection status.
 *
 * The frontend dashboard settings page calls this endpoint to display the active
 * mathematical parameters to the user.
 * ==================================================================================
 */

package com.quantstream.backend.controller;

import com.quantstream.backend.config.IndicatorProperties;
import com.quantstream.backend.config.ScoringProperties;
import com.quantstream.backend.config.SimulationProperties;
import com.quantstream.backend.config.StreamingProperties;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/config")
public class ConfigController {

    private final IndicatorProperties indicatorProperties;
    private final ScoringProperties scoringProperties;
    private final SimulationProperties simulationProperties;
    private final StreamingProperties streamingProperties;
    private final com.quantstream.backend.marketdata.MarketDataProvider marketDataProvider;

    @org.springframework.beans.factory.annotation.Value("${quantstream.marketdata.mode:simulation}")
    private String marketDataMode;

    @org.springframework.beans.factory.annotation.Value("${quantstream.marketdata.provider:FINNHUB}")
    private String marketDataProviderName;

    public ConfigController(
            IndicatorProperties indicatorProperties,
            ScoringProperties scoringProperties,
            SimulationProperties simulationProperties,
            StreamingProperties streamingProperties,
            com.quantstream.backend.marketdata.MarketDataProvider marketDataProvider
    ) {
        this.indicatorProperties = indicatorProperties;
        this.scoringProperties = scoringProperties;
        this.simulationProperties = simulationProperties;
        this.streamingProperties = streamingProperties;
        this.marketDataProvider = marketDataProvider;
    }

    /**
     * Returns the active configuration parameters across all sub-systems.
     * Route: GET /api/config
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> getConfig() {
        Map<String, Object> config = new HashMap<>();
        config.put("indicators", indicatorProperties);
        config.put("scoring", scoringProperties);
        config.put("simulation", simulationProperties);
        config.put("streaming", streamingProperties);

        Map<String, Object> marketData = new HashMap<>();
        marketData.put("mode", marketDataMode);
        marketData.put("provider", "live".equalsIgnoreCase(marketDataMode) ? marketDataProviderName : "MOCK");
        marketData.put("status", marketDataProvider.healthStatus());
        config.put("marketData", marketData);

        return ResponseEntity.ok(config);
    }
}
