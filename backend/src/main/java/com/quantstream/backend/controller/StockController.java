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

import java.util.List;

@RestController
@RequestMapping("/api/stocks")
public class StockController {

    private final AnalyticsEngine analyticsEngine;
    private final AnalyticsSnapshotRepository snapshotRepository;
    private final SymbolRepository symbolRepository;

    public StockController(
            AnalyticsEngine analyticsEngine,
            AnalyticsSnapshotRepository snapshotRepository,
            SymbolRepository symbolRepository
    ) {
        this.analyticsEngine = analyticsEngine;
        this.snapshotRepository = snapshotRepository;
        this.symbolRepository = symbolRepository;
    }

    /**
     * Lists all actively monitored stocks with their current analytics snapshot.
     */
    @GetMapping
    public ResponseEntity<List<AnalyticsSnapshot>> getAllStocks() {
        List<AnalyticsSnapshot> snapshots = analyticsEngine.getAllLatestSnapshots();

        // If no ticks have arrived yet, populate placeholder items from symbol repository
        if (snapshots.isEmpty()) {
            List<SymbolEntity> symbols = symbolRepository.findAll();
            for (SymbolEntity s : symbols) {
                analyticsEngine.getLatestSnapshot(s.getSymbol()).ifPresent(snapshots::add);
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
