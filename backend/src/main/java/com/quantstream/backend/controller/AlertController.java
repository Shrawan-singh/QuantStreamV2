package com.quantstream.backend.controller;

import com.quantstream.backend.domain.InstrumentRegistry;
import com.quantstream.backend.domain.entity.AlertConfigEntity;
import com.quantstream.backend.domain.entity.AlertTriggerHistoryEntity;
import com.quantstream.backend.repository.AlertConfigRepository;
import com.quantstream.backend.repository.AlertTriggerHistoryRepository;
import com.quantstream.backend.service.AlertExecutionService;
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

    private static final Set<String> SUPPORTED_CONDITIONS = Set.of(
            "PRICE_ABOVE", "PRICE_BELOW", "SCORE_ABOVE", "SCORE_BELOW"
    );

    private final AlertConfigRepository alertRepository;
    private final AlertTriggerHistoryRepository historyRepository;
    private final AlertExecutionService alertExecutionService;

    public AlertController(
            AlertConfigRepository alertRepository,
            AlertTriggerHistoryRepository historyRepository,
            AlertExecutionService alertExecutionService
    ) {
        this.alertRepository = alertRepository;
        this.historyRepository = historyRepository;
        this.alertExecutionService = alertExecutionService;
    }

    @GetMapping
    public ResponseEntity<List<AlertConfigEntity>> getAllAlerts() {
        return ResponseEntity.ok(alertRepository.findAll());
    }

    @GetMapping("/{id}/history")
    public ResponseEntity<List<AlertTriggerHistoryEntity>> getAlertHistory(@PathVariable @NonNull Long id) {
        return ResponseEntity.ok(historyRepository.findByAlertIdOrderByTriggeredAtDesc(id));
    }

    @GetMapping("/history")
    public ResponseEntity<List<AlertTriggerHistoryEntity>> getRecentHistory() {
        return ResponseEntity.ok(historyRepository.findTop50ByOrderByTriggeredAtDesc());
    }

    public record CreateAlertRequest(
            String symbol,
            String conditionType,
            BigDecimal threshold,
            Boolean enabled
    ) {}

    @PostMapping
    public ResponseEntity<?> createAlert(@RequestBody CreateAlertRequest request) {
        if (request == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Request body is required"));
        }

        // 1. Symbol validation
        if (request.symbol() == null || request.symbol().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Symbol is required"));
        }
        String symbol = request.symbol().trim().toUpperCase();
        if (!InstrumentRegistry.isSupported(symbol)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Instrument not found in the supported market universe."));
        }

        // 2. Condition Type validation
        if (request.conditionType() == null || request.conditionType().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "conditionType is required"));
        }
        String condition = request.conditionType().trim().toUpperCase();
        if (!SUPPORTED_CONDITIONS.contains(condition)) {
            return ResponseEntity.badRequest().body(Map.of("error",
                    "Invalid condition type. Supported: PRICE_ABOVE, PRICE_BELOW, SCORE_ABOVE, SCORE_BELOW"));
        }

        // 3. Threshold validation
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

        // 4. Duplicate prevention
        if (alertRepository.existsBySymbolAndConditionTypeAndThreshold(symbol, condition, request.threshold())) {
            return ResponseEntity.badRequest().body(Map.of("error",
                    "An identical alert already exists for this instrument, condition, and threshold."));
        }

        AlertConfigEntity entity = new AlertConfigEntity(
                symbol,
                condition,
                request.threshold(),
                request.enabled() != null ? request.enabled() : true
        );

        AlertConfigEntity saved = alertRepository.save(entity);
        return ResponseEntity.ok(saved);
    }

    @PutMapping("/{id}/toggle")
    public ResponseEntity<?> toggleAlert(@PathVariable @NonNull Long id) {
        return alertRepository.findById(id)
                .map(alert -> {
                    alert.setEnabled(!alert.isEnabled());
                    alertRepository.save(alert);
                    return ResponseEntity.ok(alert);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}/reset")
    public ResponseEntity<?> resetAlert(@PathVariable @NonNull Long id) {
        return alertExecutionService.resetAlert(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteAlert(@PathVariable @NonNull Long id) {
        if (!alertRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        alertRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
