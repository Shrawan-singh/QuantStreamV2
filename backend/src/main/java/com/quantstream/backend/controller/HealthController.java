/*
 * ==================================================================================
 * FILE: HealthController.java
 * ==================================================================================
 *
 * WHAT THIS FILE DOES:
 * This is the "HEALTH DIAGNOSTICS & SYSTEM MONITOR" endpoint.
 *
 * Route: `GET /api/health`
 *
 * WHY IS THIS CRITICAL?
 * In production trading and financial streaming applications, DevOps engineers and
 * site reliability monitors constantly poll this endpoint to verify:
 *   1. "Is the backend alive and healthy?" -> status: "UP"
 *   2. "How many ticks has it processed?" -> totalTicksProcessed
 *   3. "Is the queue backing up?" -> queueSize vs queueCapacity
 *   4. "Are the background workers running?" -> workerPoolRunning: true
 *   5. "Is the market stream connected?" -> marketDataStatus: "CONNECTED"
 *   6. "What mode are we in?" -> "live" vs "simulation"
 * ==================================================================================
 */

package com.quantstream.backend.controller;

import com.quantstream.backend.analytics.state.MarketStateStore;
import com.quantstream.backend.config.StreamingProperties;
import com.quantstream.backend.processing.TickProcessingService;
import com.quantstream.backend.processing.TickQueueService;
import com.quantstream.backend.processing.TickWorkerPool;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/health")
public class HealthController {

    private final TickProcessingService processingService;
    private final TickQueueService queueService;
    private final TickWorkerPool workerPool;
    private final MarketStateStore marketStateStore;
    private final StreamingProperties streamingProperties;
    private final com.quantstream.backend.marketdata.MarketDataProvider marketDataProvider;

    @org.springframework.beans.factory.annotation.Value("${quantstream.marketdata.mode:simulation}")
    private String marketDataMode;

    @org.springframework.beans.factory.annotation.Value("${quantstream.marketdata.provider:FINNHUB}")
    private String marketDataProviderName;

    public HealthController(
            TickProcessingService processingService,
            TickQueueService queueService,
            TickWorkerPool workerPool,
            MarketStateStore marketStateStore,
            StreamingProperties streamingProperties,
            com.quantstream.backend.marketdata.MarketDataProvider marketDataProvider
    ) {
        this.processingService = processingService;
        this.queueService = queueService;
        this.workerPool = workerPool;
        this.marketStateStore = marketStateStore;
        this.streamingProperties = streamingProperties;
        this.marketDataProvider = marketDataProvider;
    }

    /**
     * Diagnostic endpoint returning server health, queue metrics, and worker pool status.
     * Route: GET /api/health
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> getHealth() {
        Map<String, Object> health = new HashMap<>();
        health.put("status", "UP");
        health.put("application", "QuantStream Engine");
        health.put("totalTicksProcessed", processingService.getTotalProcessed());
        health.put("queueSize", queueService.size());
        health.put("queueCapacity", streamingProperties.getQueueSize());
        health.put("workerPoolRunning", workerPool.isRunning());
        health.put("workerPoolSize", streamingProperties.getWorkerPoolSize());
        health.put("trackedSymbolsCount", marketStateStore.size());
        health.put("marketDataMode", marketDataMode);
        health.put("marketDataProvider", "live".equalsIgnoreCase(marketDataMode) ? marketDataProviderName : "MOCK");
        health.put("marketDataStatus", marketDataProvider.healthStatus());
        health.put("timestamp", Instant.now());

        return ResponseEntity.ok(health);
    }
}
