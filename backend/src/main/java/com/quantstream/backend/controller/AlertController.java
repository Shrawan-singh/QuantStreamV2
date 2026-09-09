package com.quantstream.backend.controller;

import com.quantstream.backend.domain.entity.AlertConfigEntity;
import com.quantstream.backend.repository.AlertConfigRepository;
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

@RestController
@RequestMapping("/api/alerts")
public class AlertController {

    private final AlertConfigRepository alertRepository;

    public AlertController(AlertConfigRepository alertRepository) {
        this.alertRepository = alertRepository;
    }

    @GetMapping
    public ResponseEntity<List<AlertConfigEntity>> getAllAlerts() {
        return ResponseEntity.ok(alertRepository.findAll());
    }

    public record CreateAlertRequest(
            String symbol,
            String conditionType,
            BigDecimal threshold,
            Boolean enabled
    ) {}

    @PostMapping
    public ResponseEntity<?> createAlert(@RequestBody CreateAlertRequest request) {
        if (request == null || request.symbol() == null || request.conditionType() == null || request.threshold() == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "symbol, conditionType, and threshold are required"));
        }

        AlertConfigEntity entity = new AlertConfigEntity(
                request.symbol().trim().toUpperCase(),
                request.conditionType().toUpperCase(),
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

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteAlert(@PathVariable @NonNull Long id) {
        if (!alertRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        alertRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
