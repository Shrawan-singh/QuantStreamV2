/*
 * ==================================================================================
 * FILE: AlertConfigRepository.java
 * ==================================================================================
 *
 * WHAT THIS FILE DOES:
 * This is the Database Access Layer ("Repository") for user alert rules.
 *
 * WHAT IS A SPRING DATA JPA REPOSITORY?
 * In traditional Java, you had to write tedious SQL queries by hand:
 *   "SELECT * FROM alert_configs WHERE symbol = ? AND enabled = true"
 * Spring Data JPA is magic! You simply declare an interface that extends `JpaRepository`,
 * and Spring GENERATES the SQL queries for you automatically based on the method names!
 *
 * METHODS DECLARED HERE:
 * - findBySymbol(symbol):
 *   Finds all alerts created for a specific stock (e.g. all alerts for "AAPL").
 * - findByEnabledTrue():
 *   Finds all alerts that are currently turned ON.
 * - findBySymbolAndEnabledTrueAndTriggeredFalse(symbol):
 *   Finds active alerts that are armed and waiting to be triggered.
 * - existsBySymbolAndConditionTypeAndThreshold(...):
 *   Checks if an identical alert already exists to prevent duplicate rules.
 * ==================================================================================
 */

package com.quantstream.backend.repository;

import com.quantstream.backend.domain.entity.AlertConfigEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;

@Repository
public interface AlertConfigRepository extends JpaRepository<AlertConfigEntity, Long> {

    /** Finds all alerts configured for a specific symbol */
    List<AlertConfigEntity> findBySymbol(String symbol);

    /** Finds all alerts that are currently enabled */
    List<AlertConfigEntity> findByEnabledTrue();

    /** Finds armed alerts: enabled = true AND triggered = false */
    List<AlertConfigEntity> findBySymbolAndEnabledTrueAndTriggeredFalse(String symbol);

    /** Prevents the user from creating duplicate identical alerts */
    boolean existsBySymbolAndConditionTypeAndThreshold(String symbol, String conditionType, BigDecimal threshold);
}
