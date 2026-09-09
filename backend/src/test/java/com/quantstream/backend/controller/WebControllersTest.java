package com.quantstream.backend.controller;

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
import com.quantstream.backend.domain.TickEventType;
import com.quantstream.backend.domain.TickSource;
import com.quantstream.backend.domain.dto.AnalyticsSnapshot;
import com.quantstream.backend.repository.AnalyticsSnapshotRepository;
import com.quantstream.backend.repository.SymbolRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SuppressWarnings("null")
class WebControllersTest {

    private AnalyticsEngine analyticsEngine;
    private SymbolRepository symbolRepository;
    private AnalyticsSnapshotRepository snapshotRepository;
    private StockController stockController;
    private AnalyticsController analyticsController;

    @BeforeEach
    void setUp() {
        MarketStateStore store = new MarketStateStore();
        IndicatorProperties indProps = IndicatorProperties.defaultProperties();
        ScoringProperties scProps = ScoringProperties.defaultProperties();
        analyticsEngine = new AnalyticsEngine(
                store,
                new SmaIndicator(indProps),
                new EmaIndicator(indProps),
                new RsiIndicator(indProps),
                new MomentumIndicator(indProps),
                new RelativeVolumeIndicator(indProps),
                new ConvictionScoreEngine(scProps)
        );

        symbolRepository = Mockito.mock(SymbolRepository.class);
        snapshotRepository = Mockito.mock(AnalyticsSnapshotRepository.class);

        stockController = new StockController(analyticsEngine, snapshotRepository, symbolRepository);
        analyticsController = new AnalyticsController(analyticsEngine);
    }

    private void feedTick(String symbol, double price) {
        analyticsEngine.process(new StockTick(
                UUID.randomUUID(),
                symbol,
                BigDecimal.valueOf(price),
                1000L,
                Instant.now(),
                TickEventType.TRADE,
                TickSource.SIMULATION,
                "NSE"
        ));
    }

    @Test
    void stockControllerReturnsTrackedStocks() {
        feedTick("RELIANCE", 2900.0);
        feedTick("TCS", 3500.0);

        ResponseEntity<List<AnalyticsSnapshot>> response = stockController.getAllStocks();
        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals(2, response.getBody().size());

        ResponseEntity<AnalyticsSnapshot> singleResponse = stockController.getStockDetails("RELIANCE");
        assertEquals(200, singleResponse.getStatusCode().value());
        assertNotNull(singleResponse.getBody());
        assertEquals("RELIANCE", singleResponse.getBody().symbol());
        assertEquals(new BigDecimal("2900.0"), singleResponse.getBody().price());
    }

    @Test
    void analyticsControllerReturnsScannerRanking() {
        feedTick("RELIANCE", 2900.0);
        feedTick("TCS", 3500.0);

        ResponseEntity<List<AnalyticsSnapshot>> scannerResponse = analyticsController.getScanner(10, null);
        assertEquals(200, scannerResponse.getStatusCode().value());
        assertNotNull(scannerResponse.getBody());
        assertEquals(2, scannerResponse.getBody().size());

        ResponseEntity<Map<String, Object>> summaryResponse = analyticsController.getSummary();
        assertEquals(200, summaryResponse.getStatusCode().value());
        assertNotNull(summaryResponse.getBody());
        assertEquals(2, summaryResponse.getBody().get("trackedCount"));
    }
}
