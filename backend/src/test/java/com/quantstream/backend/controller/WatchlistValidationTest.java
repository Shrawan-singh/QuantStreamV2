package com.quantstream.backend.controller;

import com.quantstream.backend.analytics.AnalyticsEngine;
import com.quantstream.backend.domain.entity.WatchlistItemEntity;
import com.quantstream.backend.repository.WatchlistRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@DisplayName("Watchlist Validation Tests")
class WatchlistValidationTest {

    private WatchlistRepository watchlistRepository;
    private AnalyticsEngine analyticsEngine;
    private WatchlistController controller;

    @BeforeEach
    void setUp() {
        watchlistRepository = Mockito.mock(WatchlistRepository.class);
        analyticsEngine = Mockito.mock(AnalyticsEngine.class);
        controller = new WatchlistController(watchlistRepository, analyticsEngine);
    }

    @Test
    @DisplayName("Valid simulation instrument is trimmed, uppercased, and successfully saved")
    void testValidSimulationInstrument() {
        when(watchlistRepository.existsBySymbol("RELIANCE")).thenReturn(false);
        when(watchlistRepository.save(any(WatchlistItemEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        ResponseEntity<?> response = controller.addToWatchlist(
                new WatchlistController.AddWatchlistRequest("  reliance  ", "Energy leader")
        );

        assertEquals(200, response.getStatusCode().value());
        assertTrue(response.getBody() instanceof WatchlistItemEntity);
        WatchlistItemEntity entity = (WatchlistItemEntity) response.getBody();
        assertEquals("RELIANCE", entity.getSymbol());
    }

    @Test
    @DisplayName("Unknown arbitrary symbol is rejected with exact specification error")
    void testUnknownSymbolRejected() {
        ResponseEntity<?> response = controller.addToWatchlist(
                new WatchlistController.AddWatchlistRequest("UNKNOWN_FAKE_SYM", "Test note")
        );

        assertEquals(400, response.getStatusCode().value());
        assertTrue(response.getBody() instanceof Map);
        @SuppressWarnings("unchecked")
        Map<String, String> body = (Map<String, String>) response.getBody();
        assertEquals("Instrument not found in the supported market universe.", body.get("error"));
    }

    @Test
    @DisplayName("Empty or whitespace symbol is rejected")
    void testEmptySymbolRejected() {
        ResponseEntity<?> response = controller.addToWatchlist(
                new WatchlistController.AddWatchlistRequest("   ", "Empty note")
        );

        assertEquals(400, response.getStatusCode().value());
        @SuppressWarnings("unchecked")
        Map<String, String> body = (Map<String, String>) response.getBody();
        assertEquals("Symbol is required", body.get("error"));
    }

    @Test
    @DisplayName("Duplicate symbol entry is rejected")
    void testDuplicateSymbolRejected() {
        when(watchlistRepository.existsBySymbol("TCS")).thenReturn(true);

        ResponseEntity<?> response = controller.addToWatchlist(
                new WatchlistController.AddWatchlistRequest("TCS", "Duplicate entry")
        );

        assertEquals(400, response.getStatusCode().value());
        @SuppressWarnings("unchecked")
        Map<String, String> body = (Map<String, String>) response.getBody();
        assertEquals("Symbol already in watchlist", body.get("error"));
    }
}
