package com.quantstream.backend.controller;

import com.quantstream.backend.domain.entity.AlertConfigEntity;
import com.quantstream.backend.repository.AlertConfigRepository;
import com.quantstream.backend.repository.AlertTriggerHistoryRepository;
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

@DisplayName("Alert Validation Tests")
class AlertValidationTest {

    private AlertConfigRepository alertRepository;
    private AlertTriggerHistoryRepository historyRepository;
    private AlertExecutionService alertExecutionService;
    private AlertController controller;

    @BeforeEach
    void setUp() {
        alertRepository = Mockito.mock(AlertConfigRepository.class);
        historyRepository = Mockito.mock(AlertTriggerHistoryRepository.class);
        alertExecutionService = Mockito.mock(AlertExecutionService.class);
        controller = new AlertController(alertRepository, historyRepository, alertExecutionService);
    }

    @Test
    @DisplayName("Valid alert on supported instrument is registered")
    void testValidAlertRegistration() {
        when(alertRepository.existsBySymbolAndConditionTypeAndThreshold("RELIANCE", "PRICE_ABOVE", new BigDecimal("2900.00")))
                .thenReturn(false);
        when(alertRepository.save(any(AlertConfigEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        ResponseEntity<?> response = controller.createAlert(
                new AlertController.CreateAlertRequest("  reliance  ", "price_above", new BigDecimal("2900.00"), true)
        );

        assertEquals(200, response.getStatusCode().value());
        assertTrue(response.getBody() instanceof AlertConfigEntity);
        AlertConfigEntity saved = (AlertConfigEntity) response.getBody();
        assertEquals("RELIANCE", saved.getSymbol());
        assertEquals("PRICE_ABOVE", saved.getConditionType());
    }

    @Test
    @DisplayName("Unknown arbitrary symbol is rejected for alert")
    void testUnknownSymbolRejected() {
        ResponseEntity<?> response = controller.createAlert(
                new AlertController.CreateAlertRequest("INVALID_CO", "PRICE_ABOVE", new BigDecimal("100.00"), true)
        );

        assertEquals(400, response.getStatusCode().value());
        @SuppressWarnings("unchecked")
        Map<String, String> body = (Map<String, String>) response.getBody();
        assertEquals("Instrument not found in the supported market universe.", body.get("error"));
    }

    @Test
    @DisplayName("Empty or missing symbol is rejected")
    void testEmptySymbolRejected() {
        ResponseEntity<?> response = controller.createAlert(
                new AlertController.CreateAlertRequest("   ", "PRICE_ABOVE", new BigDecimal("100.00"), true)
        );

        assertEquals(400, response.getStatusCode().value());
        @SuppressWarnings("unchecked")
        Map<String, String> body = (Map<String, String>) response.getBody();
        assertEquals("Symbol is required", body.get("error"));
    }

    @Test
    @DisplayName("Invalid condition type is rejected")
    void testInvalidConditionTypeRejected() {
        ResponseEntity<?> response = controller.createAlert(
                new AlertController.CreateAlertRequest("TCS", "VOLUME_ABOVE", new BigDecimal("1000.00"), true)
        );

        assertEquals(400, response.getStatusCode().value());
        @SuppressWarnings("unchecked")
        Map<String, String> body = (Map<String, String>) response.getBody();
        assertTrue(body.get("error").contains("Invalid condition type"));
    }

    @Test
    @DisplayName("Non-positive threshold is rejected")
    void testNonPositiveThresholdRejected() {
        ResponseEntity<?> response = controller.createAlert(
                new AlertController.CreateAlertRequest("INFY", "PRICE_ABOVE", new BigDecimal("-50.00"), true)
        );

        assertEquals(400, response.getStatusCode().value());
        @SuppressWarnings("unchecked")
        Map<String, String> body = (Map<String, String>) response.getBody();
        assertEquals("Threshold must be greater than zero", body.get("error"));
    }

    @Test
    @DisplayName("Out-of-range score threshold is rejected")
    void testOutOfRangeScoreThresholdRejected() {
        ResponseEntity<?> response = controller.createAlert(
                new AlertController.CreateAlertRequest("INFY", "SCORE_ABOVE", new BigDecimal("150.00"), true)
        );

        assertEquals(400, response.getStatusCode().value());
        @SuppressWarnings("unchecked")
        Map<String, String> body = (Map<String, String>) response.getBody();
        assertEquals("Score threshold must be between 0.0 and 100.0", body.get("error"));
    }

    @Test
    @DisplayName("Duplicate identical alert is rejected")
    void testDuplicateAlertRejected() {
        when(alertRepository.existsBySymbolAndConditionTypeAndThreshold("HDFCBANK", "PRICE_ABOVE", new BigDecimal("1750.00")))
                .thenReturn(true);

        ResponseEntity<?> response = controller.createAlert(
                new AlertController.CreateAlertRequest("HDFCBANK", "PRICE_ABOVE", new BigDecimal("1750.00"), true)
        );

        assertEquals(400, response.getStatusCode().value());
        @SuppressWarnings("unchecked")
        Map<String, String> body = (Map<String, String>) response.getBody();
        assertTrue(body.get("error").contains("An identical alert already exists"));
    }
}
