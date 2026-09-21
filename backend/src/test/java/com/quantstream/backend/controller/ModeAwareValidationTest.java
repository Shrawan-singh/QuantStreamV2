package com.quantstream.backend.controller;

import com.quantstream.backend.analytics.AnalyticsEngine;
import com.quantstream.backend.domain.entity.AlertConfigEntity;
import com.quantstream.backend.domain.entity.WatchlistItemEntity;
import com.quantstream.backend.repository.AlertConfigRepository;
import com.quantstream.backend.repository.AlertTriggerHistoryRepository;
import com.quantstream.backend.repository.WatchlistRepository;
import com.quantstream.backend.service.AlertExecutionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Validates that watchlist and alert creation enforces mode-aware instrument validation.
 * Symbols belonging to the wrong mode's universe must be rejected with HTTP 400.
 */
@DisplayName("Mode-Aware Instrument Validation Tests")
class ModeAwareValidationTest {

    private WatchlistRepository watchlistRepository;
    private AnalyticsEngine analyticsEngine;
    private AlertConfigRepository alertRepository;
    private AlertTriggerHistoryRepository historyRepository;
    private AlertExecutionService alertExecutionService;

    @BeforeEach
    void setUp() {
        watchlistRepository = Mockito.mock(WatchlistRepository.class);
        analyticsEngine = Mockito.mock(AnalyticsEngine.class);
        alertRepository = Mockito.mock(AlertConfigRepository.class);
        historyRepository = Mockito.mock(AlertTriggerHistoryRepository.class);
        alertExecutionService = Mockito.mock(AlertExecutionService.class);
    }

    // ── Alert Tests ──

    @Test
    @DisplayName("Alert for live-only symbol AAPL is rejected in simulation mode")
    void alertForLiveSymbolRejectedInSimulationMode() {
        AlertController controller = new AlertController(
                alertRepository, historyRepository, alertExecutionService, "simulation");

        ResponseEntity<?> response = controller.createAlert(
                new AlertController.CreateAlertRequest("AAPL", "PRICE_ABOVE", new BigDecimal("200.00"), true));

        assertEquals(400, response.getStatusCode().value());
        @SuppressWarnings("unchecked")
        Map<String, String> body = (Map<String, String>) response.getBody();
        assertNotNull(body);
        assertTrue(body.get("error").contains("AAPL"));
        assertTrue(body.get("error").contains("simulation"));
    }

    @Test
    @DisplayName("Alert for simulation-only symbol RELIANCE is rejected in live mode")
    void alertForSimSymbolRejectedInLiveMode() {
        AlertController controller = new AlertController(
                alertRepository, historyRepository, alertExecutionService, "live");

        ResponseEntity<?> response = controller.createAlert(
                new AlertController.CreateAlertRequest("RELIANCE", "PRICE_ABOVE", new BigDecimal("2900.00"), true));

        assertEquals(400, response.getStatusCode().value());
        @SuppressWarnings("unchecked")
        Map<String, String> body = (Map<String, String>) response.getBody();
        assertNotNull(body);
        assertTrue(body.get("error").contains("RELIANCE"));
        assertTrue(body.get("error").contains("live"));
    }

    @Test
    @DisplayName("Valid NSE symbol is accepted for alert in simulation mode")
    void alertForValidNseSymbolAcceptedInSimulationMode() {
        AlertController controller = new AlertController(
                alertRepository, historyRepository, alertExecutionService, "simulation");

        when(alertRepository.existsBySymbolAndConditionTypeAndThreshold("TCS", "PRICE_ABOVE", new BigDecimal("4000.00")))
                .thenReturn(false);
        when(alertRepository.save(any(AlertConfigEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        ResponseEntity<?> response = controller.createAlert(
                new AlertController.CreateAlertRequest("TCS", "PRICE_ABOVE", new BigDecimal("4000.00"), true));

        assertEquals(200, response.getStatusCode().value());
        assertTrue(response.getBody() instanceof AlertConfigEntity);
    }

    // ── Watchlist Tests ──

    @Test
    @DisplayName("Watchlist entry for live-only symbol AAPL is rejected in simulation mode")
    void watchlistForLiveSymbolRejectedInSimulationMode() {
        WatchlistController controller = new WatchlistController(
                watchlistRepository, analyticsEngine, "simulation");

        ResponseEntity<?> response = controller.addToWatchlist(
                new WatchlistController.AddWatchlistRequest("AAPL", "Want to track Apple"));

        assertEquals(400, response.getStatusCode().value());
        @SuppressWarnings("unchecked")
        Map<String, String> body = (Map<String, String>) response.getBody();
        assertNotNull(body);
        assertTrue(body.get("error").contains("AAPL"));
        assertTrue(body.get("error").contains("simulation"));
    }

    @Test
    @DisplayName("Watchlist entry for simulation-only symbol RELIANCE is rejected in live mode")
    void watchlistForSimSymbolRejectedInLiveMode() {
        WatchlistController controller = new WatchlistController(
                watchlistRepository, analyticsEngine, "live");

        ResponseEntity<?> response = controller.addToWatchlist(
                new WatchlistController.AddWatchlistRequest("RELIANCE", "Indian oil"));

        assertEquals(400, response.getStatusCode().value());
        @SuppressWarnings("unchecked")
        Map<String, String> body = (Map<String, String>) response.getBody();
        assertNotNull(body);
        assertTrue(body.get("error").contains("RELIANCE"));
        assertTrue(body.get("error").contains("live"));
    }

    @Test
    @DisplayName("Valid NSE symbol is accepted for watchlist in simulation mode")
    void watchlistForValidNseSymbolAcceptedInSimulationMode() {
        WatchlistController controller = new WatchlistController(
                watchlistRepository, analyticsEngine, "simulation");

        when(watchlistRepository.existsBySymbol("INFY")).thenReturn(false);
        when(watchlistRepository.save(any(WatchlistItemEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        ResponseEntity<?> response = controller.addToWatchlist(
                new WatchlistController.AddWatchlistRequest("INFY", "IT bellwether"));

        assertEquals(200, response.getStatusCode().value());
        assertTrue(response.getBody() instanceof WatchlistItemEntity);
    }
}
