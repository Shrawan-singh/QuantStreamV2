/*
 * ==================================================================================
 * FILE: AnalyticsEngine.java
 * ==================================================================================
 *
 * WHAT THIS FILE DOES:
 * This is the "CONDUCTOR" of the analytics orchestra!
 *
 * It brings together all the individual musicians (indicators, market state,
 * and conviction scoring) to play a harmonious symphony whenever a stock price tick arrives.
 *
 * STEP-BY-STEP FLOW WHEN A TICK ARRIVES:
 * 1. Step 1: Update Chalkboard
 *    Takes the incoming tick (e.g. AAPL at $235.50) and updates the in-memory
 *    MarketStateStore chalkboard. Gets an immutable Snapshot of the stock's state.
 *
 * 2. Step 2: Run All 5 Indicators
 *    Calls SmaIndicator, EmaIndicator, RsiIndicator, MomentumIndicator, and
 *    RelativeVolumeIndicator in sequence.
 *
 * 3. Step 3: Compute Conviction Score
 *    Feeds all indicator results into ConvictionScoreEngine, which computes the
 *    0-100 score, volatility-adjusted Z-scores, and human explanations.
 *
 * 4. Step 4: Package into Unified Snapshot
 *    Bundles the market stats, indicators, conviction score, and explanations into
 *    one single comprehensive "AnalyticsSnapshot" object.
 *
 * 5. Step 5: Fast In-Memory Cache
 *    Stores the snapshot in a HashMap (`latestSnapshots`).
 *    When a frontend browser or mobile app asks "Give me AAPL's latest score",
 *    it is returned INSTANTLY from memory without having to query a slow database!
 * ==================================================================================
 */

package com.quantstream.backend.analytics;

import com.quantstream.backend.analytics.indicator.EmaIndicator;
import com.quantstream.backend.analytics.indicator.IndicatorResult;
import com.quantstream.backend.analytics.indicator.MomentumIndicator;
import com.quantstream.backend.analytics.indicator.RelativeVolumeIndicator;
import com.quantstream.backend.analytics.indicator.RsiIndicator;
import com.quantstream.backend.analytics.indicator.Signal;
import com.quantstream.backend.analytics.indicator.SmaIndicator;
import com.quantstream.backend.analytics.scoring.ConvictionScore;
import com.quantstream.backend.analytics.scoring.ConvictionScoreEngine;
import com.quantstream.backend.analytics.state.MarketState;
import com.quantstream.backend.analytics.state.MarketStateStore;
import com.quantstream.backend.domain.CompanyRegistry;
import com.quantstream.backend.domain.StockTick;
import com.quantstream.backend.domain.dto.AnalyticsSnapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Orchestrator service for the quantitative analytics pipeline.
 *
 * <p>Receives validated market ticks from worker threads, updates in-memory market state,
 * triggers quantitative indicator computations, evaluates the explainable conviction score,
 * and maintains the latest analytical snapshot for fast retrieval.</p>
 */
@Service
public class AnalyticsEngine {

    private static final Logger logger = LoggerFactory.getLogger(AnalyticsEngine.class);

    // Dependencies injected by Spring
    private final MarketStateStore marketStateStore;
    private final SmaIndicator smaIndicator;
    private final EmaIndicator emaIndicator;
    private final RsiIndicator rsiIndicator;
    private final MomentumIndicator momentumIndicator;
    private final RelativeVolumeIndicator relativeVolumeIndicator;
    private final ConvictionScoreEngine convictionScoreEngine;

    // Fast in-memory cache: Symbol -> Latest AnalyticsSnapshot
    // Allows instant response to REST API calls without touching the database
    private final ConcurrentHashMap<String, AnalyticsSnapshot> latestSnapshots = new ConcurrentHashMap<>();

    public AnalyticsEngine(
            MarketStateStore marketStateStore,
            SmaIndicator smaIndicator,
            EmaIndicator emaIndicator,
            RsiIndicator rsiIndicator,
            MomentumIndicator momentumIndicator,
            RelativeVolumeIndicator relativeVolumeIndicator,
            ConvictionScoreEngine convictionScoreEngine
    ) {
        this.marketStateStore = marketStateStore;
        this.smaIndicator = smaIndicator;
        this.emaIndicator = emaIndicator;
        this.rsiIndicator = rsiIndicator;
        this.momentumIndicator = momentumIndicator;
        this.relativeVolumeIndicator = relativeVolumeIndicator;
        this.convictionScoreEngine = convictionScoreEngine;
    }

    /**
     * Processes an incoming market tick through the full analytical pipeline.
     *
     * @param tick incoming validated stock tick
     * @return resulting point-in-time analytical snapshot
     */
    public AnalyticsSnapshot process(StockTick tick) {
        if (tick == null || tick.symbol() == null) {
            throw new IllegalArgumentException("Tick and symbol must not be null");
        }

        // 1. Update in-memory market state and obtain an immutable snapshot
        MarketState.Snapshot stateSnapshot = marketStateStore.update(tick);

        // 2. Compute all 5 quantitative indicators
        IndicatorResult smaResult = smaIndicator.calculate(stateSnapshot);
        IndicatorResult emaResult = emaIndicator.calculate(stateSnapshot);
        IndicatorResult rsiResult = rsiIndicator.calculate(stateSnapshot);
        IndicatorResult momentumResult = momentumIndicator.calculate(stateSnapshot);
        IndicatorResult rvolResult = relativeVolumeIndicator.calculate(stateSnapshot);

        // 3. Evaluate the explainable conviction score
        ConvictionScore convictionScore = convictionScoreEngine.evaluate(
                stateSnapshot, smaResult, emaResult, rsiResult, momentumResult, rvolResult
        );

        // 4. Construct unified AnalyticsSnapshot DTO
        Map<String, Double> factorScores = Map.of(
                "trend", convictionScore.trendScore(),
                "momentum", convictionScore.momentumScore(),
                "rsi", convictionScore.rsiScore(),
                "volume", convictionScore.volumeScore()
        );

        Map<String, Double> scoreBreakdown = Map.of(
                "trend", convictionScore.trendContribution(),
                "momentum", convictionScore.momentumContribution(),
                "rsi", convictionScore.rsiContribution(),
                "volume", convictionScore.volumeContribution()
        );

        Map<String, Signal> signals = convictionScore.signals();

        AnalyticsSnapshot snapshot = new AnalyticsSnapshot(
                stateSnapshot.symbol(),
                CompanyRegistry.getCompanyName(stateSnapshot.symbol()),
                stateSnapshot.latestPrice(),
                stateSnapshot.previousPrice(),
                stateSnapshot.priceChange(),
                stateSnapshot.priceChangePercent(),
                stateSnapshot.openPrice(),
                stateSnapshot.highPrice(),
                stateSnapshot.lowPrice(),
                stateSnapshot.latestVolume(),
                stateSnapshot.cumulativeVolume(),
                smaResult.value(),
                emaResult.value(),
                rsiResult.value(),
                momentumResult.value(),
                rvolResult.value(),
                convictionScore.score(),
                convictionScore.category(),
                factorScores,
                scoreBreakdown,
                signals,
                convictionScore.explanations(),
                convictionScore.ready(),
                tick.source(),
                stateSnapshot.latestTimestamp(),
                convictionScore.rawScore(),
                convictionScore.realizedVolatility()
        );

        // 5. Cache as latest snapshot for lightning-fast REST queries
        latestSnapshots.put(stateSnapshot.symbol().toUpperCase(), snapshot);

        logger.debug("Analytics computed for symbol={} score={} category={}",
                snapshot.symbol(), snapshot.convictionScore(), snapshot.scoreCategory());

        return snapshot;
    }

    /**
     * Looks up the most recent snapshot for a symbol from the in-memory cache.
     */
    public Optional<AnalyticsSnapshot> getLatestSnapshot(String symbol) {
        if (symbol == null) return Optional.empty();
        return Optional.ofNullable(latestSnapshots.get(symbol.toUpperCase()));
    }

    /**
     * Returns the latest snapshot for every actively tracked symbol.
     */
    public List<AnalyticsSnapshot> getAllLatestSnapshots() {
        return new ArrayList<>(latestSnapshots.values());
    }

    /**
     * Returns the top N highest-scoring stocks across the market right now (Leaderboard).
     */
    public List<AnalyticsSnapshot> getTopScoringStocks(int limit) {
        List<AnalyticsSnapshot> all = getAllLatestSnapshots();
        // Sort descending by conviction score (highest first)
        all.sort((a, b) -> Double.compare(b.convictionScore(), a.convictionScore()));
        if (limit > 0 && all.size() > limit) {
            return all.subList(0, limit);
        }
        return all;
    }

    /**
     * Clears all cached analytics and market state.
     */
    public void clear() {
        marketStateStore.clear();
        latestSnapshots.clear();
    }
}
