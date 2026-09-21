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

    private final MarketStateStore marketStateStore;
    private final SmaIndicator smaIndicator;
    private final EmaIndicator emaIndicator;
    private final RsiIndicator rsiIndicator;
    private final MomentumIndicator momentumIndicator;
    private final RelativeVolumeIndicator relativeVolumeIndicator;
    private final ConvictionScoreEngine convictionScoreEngine;

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

        // 1. Update in-memory state and obtain immutable snapshot
        MarketState.Snapshot stateSnapshot = marketStateStore.update(tick);

        // 2. Compute quantitative indicators
        IndicatorResult smaResult = smaIndicator.calculate(stateSnapshot);
        IndicatorResult emaResult = emaIndicator.calculate(stateSnapshot);
        IndicatorResult rsiResult = rsiIndicator.calculate(stateSnapshot);
        IndicatorResult momentumResult = momentumIndicator.calculate(stateSnapshot);
        IndicatorResult rvolResult = relativeVolumeIndicator.calculate(stateSnapshot);

        // 3. Evaluate explainable conviction score
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

        // 5. Cache as latest snapshot for REST and query performance
        latestSnapshots.put(stateSnapshot.symbol().toUpperCase(), snapshot);

        logger.debug("Analytics computed for symbol={} score={} category={}",
                snapshot.symbol(), snapshot.convictionScore(), snapshot.scoreCategory());

        return snapshot;
    }

    public Optional<AnalyticsSnapshot> getLatestSnapshot(String symbol) {
        if (symbol == null) return Optional.empty();
        return Optional.ofNullable(latestSnapshots.get(symbol.toUpperCase()));
    }

    public List<AnalyticsSnapshot> getAllLatestSnapshots() {
        return new ArrayList<>(latestSnapshots.values());
    }

    public List<AnalyticsSnapshot> getTopScoringStocks(int limit) {
        List<AnalyticsSnapshot> all = getAllLatestSnapshots();
        all.sort((a, b) -> Double.compare(b.convictionScore(), a.convictionScore()));
        if (limit > 0 && all.size() > limit) {
            return all.subList(0, limit);
        }
        return all;
    }

    public void clear() {
        marketStateStore.clear();
        latestSnapshots.clear();
    }
}
