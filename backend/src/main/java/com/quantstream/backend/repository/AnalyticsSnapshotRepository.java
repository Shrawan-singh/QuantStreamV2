/*
 * ==================================================================================
 * FILE: AnalyticsSnapshotRepository.java
 * ==================================================================================
 *
 * WHAT THIS FILE DOES:
 * Database Access Layer for historical analytics snapshots.
 *
 * WHAT THIS IS USED FOR:
 * When the user opens the frontend chart for a stock (e.g. AAPL), the frontend calls
 * the `/api/stocks/AAPL/history` endpoint to draw historical price lines and indicator
 * curves on the chart.
 *
 * `findTop50BySymbolOrderByTimestampDesc("AAPL")`:
 * Spring automatically generates the SQL to:
 *   "SELECT * FROM analytics_snapshots WHERE symbol = 'AAPL' ORDER BY timestamp DESC LIMIT 50"
 * This gives the frontend the latest 50 historical points to draw smooth chart lines.
 * ==================================================================================
 */

package com.quantstream.backend.repository;

import com.quantstream.backend.domain.entity.AnalyticsSnapshotEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AnalyticsSnapshotRepository extends JpaRepository<AnalyticsSnapshotEntity, Long> {

    /**
     * Retrieves the most recent 50 snapshots for a stock, ordered newest to oldest.
     * Used by the frontend dashboard to plot historical mini-charts.
     */
    List<AnalyticsSnapshotEntity> findTop50BySymbolOrderByTimestampDesc(String symbol);
}
