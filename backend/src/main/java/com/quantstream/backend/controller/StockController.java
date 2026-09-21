package com.quantstream.backend.controller;

import com.quantstream.backend.analytics.AnalyticsEngine;
import com.quantstream.backend.domain.dto.AnalyticsSnapshot;
import com.quantstream.backend.domain.entity.AnalyticsSnapshotEntity;
import com.quantstream.backend.domain.entity.SymbolEntity;
import com.quantstream.backend.repository.AnalyticsSnapshotRepository;
import com.quantstream.backend.repository.SymbolRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/stocks")
public class StockController {

    private final AnalyticsEngine analyticsEngine;
    private final AnalyticsSnapshotRepository snapshotRepository;
    private final SymbolRepository symbolRepository;
    private final String marketMode;

    public StockController(
            AnalyticsEngine analyticsEngine,
            AnalyticsSnapshotRepository snapshotRepository,
            SymbolRepository symbolRepository
    ) {
        this(analyticsEngine, snapshotRepository, symbolRepository, "simulation");
    }

    @org.springframework.beans.factory.annotation.Autowired
    public StockController(
            AnalyticsEngine analyticsEngine,
            AnalyticsSnapshotRepository snapshotRepository,
            SymbolRepository symbolRepository,
            @org.springframework.beans.factory.annotation.Value("${quantstream.marketdata.mode:simulation}") String marketMode
    ) {
        this.analyticsEngine = analyticsEngine;
        this.snapshotRepository = snapshotRepository;
        this.symbolRepository = symbolRepository;
        this.marketMode = marketMode;
    }

    /**
     * Lists all actively monitored stocks with their current analytics snapshot.
     */
    @GetMapping
    public ResponseEntity<List<AnalyticsSnapshot>> getAllStocks() {
        List<AnalyticsSnapshot> snapshots = analyticsEngine.getAllLatestSnapshots();

        // If no ticks have arrived yet, populate baseline items from the active market universe
        if (snapshots.isEmpty()) {
            List<com.quantstream.backend.domain.Instrument> activeUniverse =
                    com.quantstream.backend.domain.InstrumentRegistry.getActiveUniverse(marketMode);
            boolean isLive = "live".equalsIgnoreCase(marketMode);

            for (com.quantstream.backend.domain.Instrument inst : activeUniverse) {
                BigDecimal base = inst.basePrice() != null ? inst.basePrice() : new BigDecimal("100.00");
                snapshots.add(new AnalyticsSnapshot(
                        inst.symbol(),
                        inst.companyName(),
                        base,
                        base,
                        BigDecimal.ZERO,
                        0.0,
                        base,
                        base,
                        base,
                        0L,
                        0L,
                        0.0,
                        0.0,
                        50.0,
                        0.0,
                        1.0,
                        50.0,
                        com.quantstream.backend.analytics.scoring.ScoreCategory.NEUTRAL,
                        java.util.Map.of("trend", 50.0, "momentum", 50.0, "rsi", 50.0, "volume", 50.0),
                        java.util.Map.of("trend", 12.5, "momentum", 12.5, "rsi", 12.5, "volume", 12.5),
                        java.util.Map.of("trend", com.quantstream.backend.analytics.indicator.Signal.NEUTRAL,
                                "momentum", com.quantstream.backend.analytics.indicator.Signal.NEUTRAL,
                                "rsi", com.quantstream.backend.analytics.indicator.Signal.NEUTRAL,
                                "volume", com.quantstream.backend.analytics.indicator.Signal.NEUTRAL),
                        java.util.List.of(isLive ? "Connecting to Finnhub live trade stream..." : "Awaiting simulated stream ticks..."),
                        false,
                        isLive ? com.quantstream.backend.domain.TickSource.LIVE_PROVIDER : com.quantstream.backend.domain.TickSource.SIMULATION,
                        java.time.Instant.now()
                ));
            }
        }

        return ResponseEntity.ok(snapshots);
    }

    /**
     * Retrieves the latest analytical snapshot for a specific symbol.
     */
    @GetMapping("/{symbol:.+}")
    public ResponseEntity<AnalyticsSnapshot> getStockDetails(@PathVariable String symbol) {
        return analyticsEngine.getLatestSnapshot(symbol)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Retrieves historical snapshot observations for charting.
     */
    @GetMapping("/{symbol:.+}/history")
    public ResponseEntity<List<AnalyticsSnapshotEntity>> getStockHistory(@PathVariable String symbol) {
        List<AnalyticsSnapshotEntity> history = snapshotRepository.findTop50BySymbolOrderByTimestampDesc(symbol.toUpperCase());
        return ResponseEntity.ok(history);
    }
}
