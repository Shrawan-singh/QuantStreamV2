/*
 * ==================================================================================
 * FILE: AlertController.java
 * ==================================================================================
 *
 * WHAT THIS FILE DOES:
 * This is the REST API Controller for managing User Price & Conviction Alerts.
 *
 * It allows the frontend to:
 *   1. View existing alert rules (`GET /api/alerts`)
 *   2. Create new alert rules (`POST /api/alerts`)
 *   3. Toggle rules on/off (`PUT /api/alerts/{id}/toggle`)
 *   4. Re-arm triggered alerts (`PUT /api/alerts/{id}/reset`)
 *   5. Delete alerts (`DELETE /api/alerts/{id}`)
 *   6. View past trigger history (`GET /api/alerts/history`)
 *
 * INPUT VALIDATION RULES ENFORCED:
 * - Symbol must exist in the currently active universe (simulation vs live).
 * - Threshold must be positive (e.g. price > 0).
 * - Conviction score thresholds must be within 0.0 to 100.0.
 * - Duplicates are rejected: user cannot create two identical active alerts.
 * ==================================================================================
 */

package com.quantstream.backend.controller;

import com.quantstream.backend.domain.InstrumentRegistry;
import com.quantstream.backend.domain.entity.AlertConfigEntity;
import com.quantstream.backend.domain.entity.AlertTriggerHistoryEntity;
import com.quantstream.backend.repository.AlertConfigRepository;
import com.quantstream.backend.repository.AlertTriggerHistoryRepository;
import com.quantstream.backend.service.AlertExecutionService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.NonNull;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/alerts")
public class AlertController {

    // Valid supported condition types
    private static final Set<String> SUPPORTED_CONDITIONS = Set.of(
            "PRICE_ABOVE", "PRICE_BELOW", "SCORE_ABOVE", "SCORE_BELOW",
            "CONVICTION_ABOVE", "CONVICTION_BELOW"
    );

    private final AlertConfigRepository alertRepository;
    private final AlertTriggerHistoryRepository historyRepository;
    private final AlertExecutionService alertExecutionService;
    private final String marketMode;

    public AlertController(
            AlertConfigRepository alertRepository,
            AlertTriggerHistoryRepository historyRepository,
            AlertExecutionService alertExecutionService,
            @Value("${quantstream.marketdata.mode:simulation}") String marketMode
    ) {
        this.alertRepository = alertRepository;
        this.historyRepository = historyRepository;
        this.alertExecutionService = alertExecutionService;
        this.marketMode = marketMode;
    }

    /**
     * Returns all configured alert rules in the database.
     * Route: GET /api/alerts
     */
    @GetMapping
    public ResponseEntity<List<AlertConfigEntity>> getAllAlerts() {
        return ResponseEntity.ok(alertRepository.findAll());
    }

    /**
     * Returns trigger history for a specific alert.
     * Route: GET /api/alerts/{id}/history
     */
    @GetMapping("/{id}/history")
    public ResponseEntity<List<AlertTriggerHistoryEntity>> getAlertHistory(@PathVariable @NonNull Long id) {
        return ResponseEntity.ok(historyRepository.findByAlertIdOrderByTriggeredAtDesc(id));
    }

    /**
     * Returns the 50 most recent alert triggers across all stocks.
     * Route: GET /api/alerts/history
     */
    @GetMapping("/history")
    public ResponseEntity<List<AlertTriggerHistoryEntity>> getRecentHistory() {
        return ResponseEntity.ok(historyRepository.findTop50ByOrderByTriggeredAtDesc());
    }

    /**
     * Request payload sent by frontend when creating a new alert.
     */
    public record CreateAlertRequest(
            String symbol,
            String conditionType,
            BigDecimal threshold,
            Boolean enabled
    ) {}

    /**
     * Creates a new alert rule with strict validation.
     * Route: POST /api/alerts
     */
    @PostMapping
    public ResponseEntity<?> createAlert(@RequestBody CreateAlertRequest request) {
        if (request == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Request body is required"));
        }

        // 1. Symbol validation: Stock must be valid in the active market mode
        if (request.symbol() == null || request.symbol().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Symbol is required"));
        }
        String symbol = request.symbol().trim().toUpperCase();
        if (!InstrumentRegistry.isSupportedInCurrentMode(symbol, marketMode)) {
            String modeName = "live".equalsIgnoreCase(marketMode) ? "live (US equities)" : "simulation (NSE equities)";
            return ResponseEntity.badRequest().body(Map.of("error",
                    "Symbol '" + symbol + "' is not available in the current " + modeName + " mode universe."));
        }

        // 2. Condition Type validation & normalization (e.g. CONVICTION_ABOVE -> SCORE_ABOVE)
        if (request.conditionType() == null || request.conditionType().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "conditionType is required"));
        }
        String rawCondition = request.conditionType().trim().toUpperCase();
        if (!SUPPORTED_CONDITIONS.contains(rawCondition)) {
            return ResponseEntity.badRequest().body(Map.of("error",
                    "Invalid condition type. Supported: PRICE_ABOVE, PRICE_BELOW, SCORE_ABOVE, SCORE_BELOW (or CONVICTION_ABOVE, CONVICTION_BELOW)"));
        }
        String condition = switch (rawCondition) {
            case "CONVICTION_ABOVE" -> "SCORE_ABOVE";
            case "CONVICTION_BELOW" -> "SCORE_BELOW";
            default -> rawCondition;
        };

        // 3. Threshold validation (must be positive number; scores must be 0-100)
        if (request.threshold() == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Numeric threshold is required"));
        }
        if (request.threshold().compareTo(BigDecimal.ZERO) <= 0) {
            return ResponseEntity.badRequest().body(Map.of("error", "Threshold must be greater than zero"));
        }
        if (condition.startsWith("SCORE_")) {
            double thresholdVal = request.threshold().doubleValue();
            if (thresholdVal < 0.0 || thresholdVal > 100.0) {
                return ResponseEntity.badRequest().body(Map.of("error", "Score threshold must be between 0.0 and 100.0"));
            }
        }

        // 4. Prevent duplicate identical rules
        if (alertRepository.existsBySymbolAndConditionTypeAndThreshold(symbol, condition, request.threshold())) {
            return ResponseEntity.badRequest().body(Map.of("error",
                    "An identical alert already exists for this instrument, condition, and threshold."));
        }

        // 5. Save entity and invalidate memory cache so the stream picks up the new rule immediately
        AlertConfigEntity entity = new AlertConfigEntity(
                symbol,
                condition,
                request.threshold(),
                request.enabled() != null ? request.enabled() : true
        );

        AlertConfigEntity saved = alertRepository.save(entity);
        alertExecutionService.invalidateCache(symbol);
        return ResponseEntity.ok(saved);
    }

    /**
     * Toggles an alert between enabled and disabled.
     * Route: PUT /api/alerts/{id}/toggle
     */
    @PutMapping("/{id}/toggle")
    public ResponseEntity<?> toggleAlert(@PathVariable @NonNull Long id) {
        return alertRepository.findById(id)
                .map(alert -> {
                    alert.setEnabled(!alert.isEnabled());
                    AlertConfigEntity saved = alertRepository.save(alert);
                    alertExecutionService.invalidateCache(saved.getSymbol());
                    return ResponseEntity.ok(saved);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Resets a triggered alert back to active (re-arm).
     * Route: PUT /api/alerts/{id}/reset
     */
    @PutMapping("/{id}/reset")
    public ResponseEntity<?> resetAlert(@PathVariable @NonNull Long id) {
        return alertExecutionService.resetAlert(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Deletes an alert rule from the database.
     * Route: DELETE /api/alerts/{id}
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteAlert(@PathVariable @NonNull Long id) {
        return alertRepository.findById(id)
                .map(alert -> {
                    alertRepository.deleteById(id);
                    alertExecutionService.invalidateCache(alert.getSymbol());
                    return ResponseEntity.noContent().build();
                })
                .orElse(ResponseEntity.notFound().build());
    }
}
