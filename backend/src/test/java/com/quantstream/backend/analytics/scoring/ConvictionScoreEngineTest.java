package com.quantstream.backend.analytics.scoring;

import com.quantstream.backend.analytics.indicator.IndicatorResult;
import com.quantstream.backend.analytics.indicator.Signal;
import com.quantstream.backend.analytics.state.MarketState;
import com.quantstream.backend.config.ScoringProperties;
import com.quantstream.backend.domain.StockTick;
import com.quantstream.backend.domain.TickEventType;
import com.quantstream.backend.domain.TickSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ConvictionScoreEngineTest {

    private ConvictionScoreEngine engine;
    private MarketState.Snapshot snapshot;

    @BeforeEach
    void setUp() {
        engine = new ConvictionScoreEngine(new ScoringProperties(25.0, 25.0, 25.0, 25.0));

        MarketState state = new MarketState("RELIANCE", 20);
        state.update(new StockTick(
                UUID.randomUUID(),
                "RELIANCE",
                BigDecimal.valueOf(2500.0),
                1000L,
                Instant.now(),
                TickEventType.TRADE,
                TickSource.SIMULATION,
                "NSE"
        ));
        snapshot = state.getSnapshot();
    }

    @Test
    void allIndicatorsNotReadyProducesDefaultNeutralScore() {
        ConvictionScore score = engine.evaluate(
                snapshot,
                IndicatorResult.notReady(),
                IndicatorResult.notReady(),
                IndicatorResult.notReady(),
                IndicatorResult.notReady(),
                IndicatorResult.notReady()
        );

        assertFalse(score.ready());
        assertEquals(50.0, score.score());
        assertEquals(ScoreCategory.NEUTRAL, score.category());
        assertEquals(1, score.explanations().size());
    }

    @Test
    void allPositiveSignalsProduce100ScoreAndVeryStrongCategory() {
        IndicatorResult pos = new IndicatorResult(100.0, Signal.POSITIVE, true);

        ConvictionScore score = engine.evaluate(snapshot, pos, pos, pos, pos, pos);

        assertTrue(score.ready());
        assertEquals(100.0, score.score());
        assertEquals(ScoreCategory.VERY_STRONG, score.category());
        assertEquals(25.0, score.trendContribution());
        assertEquals(25.0, score.momentumContribution());
        assertEquals(25.0, score.rsiContribution());
        assertEquals(25.0, score.volumeContribution());
        assertEquals(4, score.explanations().size());
    }

    @Test
    void allNegativeSignalsProduceZeroScoreAndVeryWeakCategory() {
        IndicatorResult neg = new IndicatorResult(50.0, Signal.NEGATIVE, true);

        ConvictionScore score = engine.evaluate(snapshot, neg, neg, neg, neg, neg);

        assertTrue(score.ready());
        assertEquals(0.0, score.score());
        assertEquals(ScoreCategory.VERY_WEAK, score.category());
        assertEquals(0.0, score.trendContribution());
        assertEquals(0.0, score.momentumContribution());
    }

    @Test
    void mixedSignalsProduceExpectedScoreBreakdown() {
        IndicatorResult trend = new IndicatorResult(2400.0, Signal.POSITIVE, true); // +25
        IndicatorResult ema = new IndicatorResult(2450.0, Signal.POSITIVE, true);
        IndicatorResult rsi = new IndicatorResult(50.0, Signal.NEUTRAL, true);      // +12.5
        IndicatorResult mom = new IndicatorResult(-2.0, Signal.NEGATIVE, true);     // +0.0
        IndicatorResult vol = new IndicatorResult(1.8, Signal.POSITIVE, true);      // +25

        ConvictionScore score = engine.evaluate(snapshot, trend, ema, rsi, mom, vol);

        assertTrue(score.ready());
        // 25 + 0 + 12.5 + 25 = 62.5 -> STRONG
        assertEquals(62.5, score.score(), 0.1);
        assertEquals(ScoreCategory.STRONG, score.category());
        assertEquals(25.0, score.trendContribution());
        assertEquals(0.0, score.momentumContribution());
        assertEquals(12.5, score.rsiContribution());
        assertEquals(25.0, score.volumeContribution());
    }

    @Test
    void allNeutralSignalsProduceFiftyScoreAndNeutralCategory() {
        IndicatorResult neutral = new IndicatorResult(50.0, Signal.NEUTRAL, true);

        ConvictionScore score = engine.evaluate(snapshot, neutral, neutral, neutral, neutral, neutral);

        assertTrue(score.ready());
        assertEquals(50.0, score.score());
        assertEquals(ScoreCategory.NEUTRAL, score.category());
        assertEquals(12.5, score.trendContribution());
        assertEquals(12.5, score.momentumContribution());
        assertEquals(12.5, score.rsiContribution());
        assertEquals(12.5, score.volumeContribution());
    }

    @Test
    void partialOrNullIndicatorsHandledSafelyWithoutExceptions() {
        // Only 1 indicator ready, others null
        IndicatorResult readyRsi = new IndicatorResult(70.0, Signal.POSITIVE, true);

        ConvictionScore score = engine.evaluate(snapshot, null, null, readyRsi, null, null);

        assertTrue(score.ready());
        // Trend = 12.5 (NOT_READY), Momentum = 12.5 (NOT_READY), RSI = 25.0 (POSITIVE), Volume = 12.5 (NOT_READY) -> 62.5
        assertEquals(62.5, score.score(), 0.1);
        assertEquals(ScoreCategory.STRONG, score.category());
        assertEquals(12.5, score.trendContribution());
        assertEquals(25.0, score.rsiContribution());
    }

    @Test
    void scoreIsAlwaysStrictlyClampedBetweenZeroAndOneHundred() {
        // Test extremes
        IndicatorResult pos = new IndicatorResult(100.0, Signal.POSITIVE, true);
        IndicatorResult neg = new IndicatorResult(0.0, Signal.NEGATIVE, true);

        ConvictionScore maxScore = engine.evaluate(snapshot, pos, pos, pos, pos, pos);
        assertTrue(maxScore.score() <= 100.0);
        assertTrue(maxScore.score() >= 0.0);

        ConvictionScore minScore = engine.evaluate(snapshot, neg, neg, neg, neg, neg);
        assertTrue(minScore.score() <= 100.0);
        assertTrue(minScore.score() >= 0.0);
    }

    @Test
    void exactCategoryThresholdBoundaries() {
        assertEquals(ScoreCategory.VERY_WEAK, ScoreCategory.fromScore(0.0));
        assertEquals(ScoreCategory.VERY_WEAK, ScoreCategory.fromScore(20.0));
        assertEquals(ScoreCategory.WEAK, ScoreCategory.fromScore(20.1));
        assertEquals(ScoreCategory.WEAK, ScoreCategory.fromScore(40.0));
        assertEquals(ScoreCategory.NEUTRAL, ScoreCategory.fromScore(40.1));
        assertEquals(ScoreCategory.NEUTRAL, ScoreCategory.fromScore(60.0));
        assertEquals(ScoreCategory.STRONG, ScoreCategory.fromScore(60.1));
        assertEquals(ScoreCategory.STRONG, ScoreCategory.fromScore(80.0));
        assertEquals(ScoreCategory.VERY_STRONG, ScoreCategory.fromScore(80.1));
        assertEquals(ScoreCategory.VERY_STRONG, ScoreCategory.fromScore(100.0));
    }
}
