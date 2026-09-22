/*
 * ==================================================================================
 * FILE: TickProcessingService.java
 * ==================================================================================
 *
 * WHAT THIS FILE DOES:
 * This is the central "WORKBENCH" where raw market ticks are turned into live insights!
 *
 * Each worker thread takes a tick off the conveyor belt (TickQueueService) and brings
 * it to this service for processing.
 *
 * THE 6-STEP WORKBENCH ASSEMBLY LINE:
 * 1. Validation:
 *    Double-checks that price, symbol, and timestamp are valid.
 * 2. Counter Metrics:
 *    Increments total tick counter and per-symbol counters (useful for health diagnostics).
 * 3. Run Analytics Pipeline:
 *    Sends the tick to `AnalyticsEngine`, which updates SMA, EMA, RSI, Momentum,
 *    and generates the unified `AnalyticsSnapshot` with the Conviction Score!
 * 4. Live WebSocket Broadcast:
 *    Instantly pushes the new price and conviction score out to connected web browsers
 *    and dashboards so the user sees live charts updating in real time!
 * 5. Autonomous Alert Evaluation:
 *    Checks if the user set any alerts (e.g. "Alert me if AAPL score > 80").
 *    If triggered, fires a notification!
 * 6. Asynchronous Persistence:
 *    Hands the snapshot to a background queue to be saved to the PostgreSQL/H2 database
 *    WITHOUT slowing down the live streaming hot path!
 * ==================================================================================
 */

package com.quantstream.backend.processing;

import com.quantstream.backend.analytics.AnalyticsEngine;
import com.quantstream.backend.analytics.indicator.EmaIndicator;
import com.quantstream.backend.analytics.indicator.MomentumIndicator;
import com.quantstream.backend.analytics.indicator.RelativeVolumeIndicator;
import com.quantstream.backend.analytics.indicator.RsiIndicator;
import com.quantstream.backend.analytics.indicator.SmaIndicator;
import com.quantstream.backend.analytics.scoring.ConvictionScoreEngine;
import com.quantstream.backend.analytics.state.MarketStateStore;
import com.quantstream.backend.config.IndicatorProperties;
import com.quantstream.backend.config.ScoringProperties;
import com.quantstream.backend.domain.StockTick;
import com.quantstream.backend.domain.dto.AnalyticsSnapshot;
import com.quantstream.backend.service.AlertExecutionService;
import com.quantstream.backend.service.AnalyticsPersistenceService;
import com.quantstream.backend.websocket.MarketWebSocketService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * High-throughput worker processing service.
 *
 * <p>Validates ticks, updates tracking metrics, orchestrates in-memory quantitative analytics,
 * broadcasts results to WebSocket clients, evaluates autonomous alerts, and enqueues snapshots
 * for asynchronous persistence.</p>
 */
@Service
public class TickProcessingService {

    private static final Logger logger = LoggerFactory.getLogger(TickProcessingService.class);

    private final TickValidationService tickValidationService;
    private final AnalyticsEngine analyticsEngine;
    private final MarketWebSocketService webSocketService;
    private final AnalyticsPersistenceService persistenceService;
    private final AlertExecutionService alertExecutionService;

    // High-performance atomic counters for throughput metrics
    private final AtomicLong totalProcessed = new AtomicLong(0L);
    private final ConcurrentHashMap<String, AtomicLong> processedBySymbol = new ConcurrentHashMap<>();

    @Autowired
    public TickProcessingService(
            TickValidationService tickValidationService,
            AnalyticsEngine analyticsEngine,
            MarketWebSocketService webSocketService,
            AnalyticsPersistenceService persistenceService,
            @Autowired(required = false) AlertExecutionService alertExecutionService
    ) {
        this.tickValidationService = tickValidationService;
        this.analyticsEngine = analyticsEngine;
        this.webSocketService = webSocketService;
        this.persistenceService = persistenceService;
        this.alertExecutionService = alertExecutionService;
    }

    /**
     * Convenience constructor for unit testing without full Spring context.
     */
    public TickProcessingService(TickValidationService tickValidationService) {
        this(
                tickValidationService,
                createDefaultAnalyticsEngine(),
                null,
                null,
                null
        );
    }

    public TickProcessingService(TickValidationService tickValidationService, AnalyticsEngine analyticsEngine) {
        this(
                tickValidationService,
                analyticsEngine,
                null,
                null,
                null
        );
    }

    private static AnalyticsEngine createDefaultAnalyticsEngine() {
        IndicatorProperties indProps = IndicatorProperties.defaultProperties();
        ScoringProperties scProps = ScoringProperties.defaultProperties();
        return new AnalyticsEngine(
                new MarketStateStore(),
                new SmaIndicator(indProps),
                new EmaIndicator(indProps),
                new RsiIndicator(indProps),
                new MomentumIndicator(indProps),
                new RelativeVolumeIndicator(indProps),
                new ConvictionScoreEngine(scProps)
        );
    }

    /**
     * The master processing step executed for every single tick.
     */
    public void process(StockTick tick) {
        // Step 1: Validation
        tickValidationService.validate(tick);

        // Step 2: Metrics counting (atomic thread-safe increment)
        long total = totalProcessed.incrementAndGet();
        processedBySymbol.computeIfAbsent(tick.symbol(), symbol -> new AtomicLong()).incrementAndGet();

        // Step 3: Run the full quantitative analytics pipeline (SMA, EMA, RSI, Momentum, Conviction Score)
        AnalyticsSnapshot snapshot = analyticsEngine.process(tick);

        // Step 4: Real-time WebSocket broadcast out to connected user browsers
        if (webSocketService != null) {
            webSocketService.broadcast(snapshot);
        }

        // Step 5: Check user-configured alerts (e.g. Price > X or Score > Y)
        if (alertExecutionService != null) {
            alertExecutionService.evaluate(snapshot);
        }

        // Step 6: Queue snapshot for database saving off the hot execution path
        if (persistenceService != null) {
            persistenceService.enqueue(snapshot);
        }

        logger.debug("Processed tick total={} symbol={} price={} score={} thread={}",
                total,
                tick.symbol(),
                tick.price(),
                snapshot.convictionScore(),
                Thread.currentThread().getName());
    }

    public long getTotalProcessed() {
        return totalProcessed.get();
    }

    public long getProcessedForSymbol(String symbol) {
        AtomicLong symbolCount = processedBySymbol.get(symbol);
        return symbolCount == null ? 0L : symbolCount.get();
    }

    public AnalyticsEngine getAnalyticsEngine() {
        return analyticsEngine;
    }
}