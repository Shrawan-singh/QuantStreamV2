/*
 * ==================================================================================
 * FILE: AlertTriggerHistoryRepository.java
 * ==================================================================================
 *
 * WHAT THIS FILE DOES:
 * Database Access Layer for the audit log of fired alerts.
 *
 * Every time an alert fires (e.g. "AAPL price crossed $230.00 at 10:15 AM"), a permanent
 * record is saved in the `alert_trigger_history` table.
 *
 * QUERIES PROVIDED:
 * - findByAlertIdOrderByTriggeredAtDesc(alertId):
 *   Shows all times this specific alert rule has fired in the past.
 * - findBySymbolOrderByTriggeredAtDesc(symbol):
 *   Shows all alerts fired for this stock symbol.
 * - findTop50ByOrderByTriggeredAtDesc():
 *   Shows the 50 most recent notifications for the frontend alert notification bell!
 * ==================================================================================
 */

package com.quantstream.backend.repository;

import com.quantstream.backend.domain.entity.AlertTriggerHistoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AlertTriggerHistoryRepository extends JpaRepository<AlertTriggerHistoryEntity, Long> {

    /** History of triggers for a specific alert rule */
    List<AlertTriggerHistoryEntity> findByAlertIdOrderByTriggeredAtDesc(Long alertId);

    /** History of triggers for a specific stock */
    List<AlertTriggerHistoryEntity> findBySymbolOrderByTriggeredAtDesc(String symbol);

    /** Top 50 most recent alert trigger notifications across the entire platform */
    List<AlertTriggerHistoryEntity> findTop50ByOrderByTriggeredAtDesc();
}
