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
 * broadcasts results to WebSocket clients, and enqueues snapshots for asynchronous persistence.</p>
 */
@Service
public class TickProcessingService {

    private static final Logger logger = LoggerFactory.getLogger(TickProcessingService.class);

    private final TickValidationService tickValidationService;
    private final AnalyticsEngine analyticsEngine;
    private final MarketWebSocketService webSocketService;
    private final AnalyticsPersistenceService persistenceService;

    private final AtomicLong totalProcessed = new AtomicLong(0L);
    private final ConcurrentHashMap<String, AtomicLong> processedBySymbol = new ConcurrentHashMap<>();

    @Autowired
    public TickProcessingService(
            TickValidationService tickValidationService,
            AnalyticsEngine analyticsEngine,
            MarketWebSocketService webSocketService,
            AnalyticsPersistenceService persistenceService
    ) {
        this.tickValidationService = tickValidationService;
        this.analyticsEngine = analyticsEngine;
        this.webSocketService = webSocketService;
        this.persistenceService = persistenceService;
    }

    /**
     * Convenience constructor for unit testing without full Spring context.
     */
    public TickProcessingService(TickValidationService tickValidationService) {
        this(
                tickValidationService,
                createDefaultAnalyticsEngine(),
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

    public void process(StockTick tick) {
        // 1. Validation
        tickValidationService.validate(tick);

        // 2. Metrics counting
        long total = totalProcessed.incrementAndGet();
        processedBySymbol.computeIfAbsent(tick.symbol(), symbol -> new AtomicLong()).incrementAndGet();

        // 3. Quantitative Analytics Pipeline
        AnalyticsSnapshot snapshot = analyticsEngine.process(tick);

        // 4. Real-time WebSocket Broadcast (fire-and-forget, non-blocking)
        if (webSocketService != null) {
            webSocketService.broadcast(snapshot);
        }

        // 5. Asynchronous persistence and alert evaluation (off hot path)
        if (persistenceService != null) {
            persistenceService.enqueue(snapshot);
        }

        logger.info("Processed tick total={} symbol={} price={} score={} thread={}",
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