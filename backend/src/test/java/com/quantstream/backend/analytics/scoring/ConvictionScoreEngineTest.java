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
    void allMaxIndicatorsProduce100ScoreAndVeryStrongCategory() {
        // Price = 2500 vs SMA = 2400 (> +2% diff -> Trend = 100.0)
        IndicatorResult trend = new IndicatorResult(2400.0, Signal.POSITIVE, true);
        IndicatorResult ema = new IndicatorResult(2400.0, Signal.POSITIVE, true);
        IndicatorResult rsi = new IndicatorResult(100.0, Signal.POSITIVE, true);
        IndicatorResult mom = new IndicatorResult(2.5, Signal.POSITIVE, true); // > +2% -> Mom = 100.0
        IndicatorResult vol = new IndicatorResult(2.0, Signal.POSITIVE, true); // 2.0x -> Vol = 100.0

        ConvictionScore score = engine.evaluate(snapshot, trend, ema, rsi, mom, vol);

        assertTrue(score.ready());
        assertEquals(100.0, score.score());
        assertEquals(ScoreCategory.VERY_STRONG, score.category());
        assertEquals(100.0, score.trendScore());
        assertEquals(100.0, score.momentumScore());
        assertEquals(100.0, score.rsiScore());
        assertEquals(100.0, score.volumeScore());
        assertEquals(25.0, score.trendContribution());
        assertEquals(25.0, score.momentumContribution());
        assertEquals(25.0, score.rsiContribution());
        assertEquals(25.0, score.volumeContribution());
        assertEquals(4, score.explanations().size());
    }

    @Test
    void allMinIndicatorsProduceZeroScoreAndVeryWeakCategory() {
        // Price = 2500 vs SMA = 2600 (< -2% diff -> Trend = 0.0)
        IndicatorResult trend = new IndicatorResult(2600.0, Signal.NEGATIVE, true);
        IndicatorResult ema = new IndicatorResult(2600.0, Signal.NEGATIVE, true);
        IndicatorResult rsi = new IndicatorResult(0.0, Signal.NEGATIVE, true);
        IndicatorResult mom = new IndicatorResult(-2.5, Signal.NEGATIVE, true); // < -2% -> Mom = 0.0
        IndicatorResult vol = new IndicatorResult(0.0, Signal.NEGATIVE, true); // 0.0x -> Vol = 0.0

        ConvictionScore score = engine.evaluate(snapshot, trend, ema, rsi, mom, vol);

        assertTrue(score.ready());
        assertEquals(0.0, score.score());
        assertEquals(ScoreCategory.VERY_WEAK, score.category());
        assertEquals(0.0, score.trendScore());
        assertEquals(0.0, score.momentumScore());
        assertEquals(0.0, score.rsiScore());
        assertEquals(0.0, score.volumeScore());
        assertEquals(0.0, score.trendContribution());
        assertEquals(0.0, score.momentumContribution());
        assertEquals(0.0, score.rsiContribution());
        assertEquals(0.0, score.volumeContribution());
    }

    @Test
    void continuousPromptExampleValidation() {
        // Test exact example from user specification:
        // Trend: 78.0 -> diffPct = +1.12% -> 50 + (1.12 / 2.0) * 50 = 78.0
        // Momentum: 64.0 -> momVal = +0.56% -> 50 + (0.56 / 2.0) * 50 = 64.0
        // RSI: 71.0 -> 71.0
        // Volume: 83.0 -> rvol = 1.66x -> 1.66 * 50 = 83.0
        // Conviction: (78 + 64 + 71 + 83) / 4 = 296 / 4 = 74.0

        // snapshot price is 2500.0. To get +1.12% diff: SMA = 2500 / 1.0112 = 2472.3101
        double smaVal = 2500.0 / 1.0112;
        IndicatorResult trend = new IndicatorResult(smaVal, Signal.POSITIVE, true);
        IndicatorResult ema = new IndicatorResult(smaVal, Signal.POSITIVE, true);
        IndicatorResult rsi = new IndicatorResult(71.0, Signal.POSITIVE, true);
        IndicatorResult mom = new IndicatorResult(0.56, Signal.POSITIVE, true);
        IndicatorResult vol = new IndicatorResult(1.66, Signal.POSITIVE, true);

        ConvictionScore score = engine.evaluate(snapshot, trend, ema, rsi, mom, vol);

        assertTrue(score.ready());
        assertEquals(78.0, score.trendScore(), 0.1);
        assertEquals(64.0, score.momentumScore(), 0.1);
        assertEquals(71.0, score.rsiScore(), 0.1);
        assertEquals(83.0, score.volumeScore(), 0.1);
        assertEquals(74.0, score.score(), 0.1);
        assertEquals(ScoreCategory.STRONG, score.category());
    }

    @Test
    void allNeutralSignalsProduceFiftyScoreAndNeutralCategory() {
        // Price = 2500 vs SMA = 2500 -> Trend = 50.0
        IndicatorResult trend = new IndicatorResult(2500.0, Signal.NEUTRAL, true);
        IndicatorResult ema = new IndicatorResult(2500.0, Signal.NEUTRAL, true);
        IndicatorResult rsi = new IndicatorResult(50.0, Signal.NEUTRAL, true);
        IndicatorResult mom = new IndicatorResult(0.0, Signal.NEUTRAL, true);
        IndicatorResult vol = new IndicatorResult(1.0, Signal.NEUTRAL, true); // 1.0x -> 50.0

        ConvictionScore score = engine.evaluate(snapshot, trend, ema, rsi, mom, vol);

        assertTrue(score.ready());
        assertEquals(50.0, score.score());
        assertEquals(ScoreCategory.NEUTRAL, score.category());
        assertEquals(50.0, score.trendScore());
        assertEquals(50.0, score.momentumScore());
        assertEquals(50.0, score.rsiScore());
        assertEquals(50.0, score.volumeScore());
        assertEquals(12.5, score.trendContribution());
        assertEquals(12.5, score.momentumContribution());
        assertEquals(12.5, score.rsiContribution());
        assertEquals(12.5, score.volumeContribution());
    }

    @Test
    void partialOrNullIndicatorsHandledSafelyWithoutExceptions() {
        IndicatorResult readyRsi = new IndicatorResult(70.0, Signal.POSITIVE, true);

        ConvictionScore score = engine.evaluate(snapshot, null, null, readyRsi, null, null);

        assertTrue(score.ready());
        // Trend = 50.0 (contrib 12.5), Mom = 50.0 (contrib 12.5), Vol = 50.0 (contrib 12.5), RSI = 70.0 (contrib 17.5)
        // Score = 12.5 + 12.5 + 12.5 + 17.5 = 55.0
        assertEquals(55.0, score.score(), 0.1);
        assertEquals(ScoreCategory.NEUTRAL, score.category());
        assertEquals(12.5, score.trendContribution());
        assertEquals(17.5, score.rsiContribution());
    }

    @Test
    void scoreIsAlwaysStrictlyClampedBetweenZeroAndOneHundred() {
        IndicatorResult pos = new IndicatorResult(999.0, Signal.POSITIVE, true);
        IndicatorResult neg = new IndicatorResult(-999.0, Signal.NEGATIVE, true);

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
