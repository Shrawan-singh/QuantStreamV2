package com.quantstream.backend.analytics.scoring;

import com.quantstream.backend.analytics.indicator.IndicatorResult;
import com.quantstream.backend.analytics.indicator.Signal;
import com.quantstream.backend.analytics.state.MarketState;
import com.quantstream.backend.config.ScoringProperties;
import com.quantstream.backend.domain.StockTick;
import com.quantstream.backend.domain.TickEventType;
import com.quantstream.backend.domain.TickSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Continuous Conviction Scoring Granularity & Explainability Test")
class ConvictionScoreEngineContinuousTest {

    private ConvictionScoreEngine engine;
    private MarketState.Snapshot snapshot;

    @BeforeEach
    void setUp() {
        // Equal 25% weights
        engine = new ConvictionScoreEngine(new ScoringProperties(25.0, 25.0, 25.0, 25.0));

        MarketState state = new MarketState("RELIANCE", 20);
        state.update(new StockTick(
                UUID.randomUUID(),
                "RELIANCE",
                BigDecimal.valueOf(2850.00),
                500_000L,
                Instant.now(),
                TickEventType.TRADE,
                TickSource.SIMULATION,
                "NSE"
        ));
        snapshot = state.getSnapshot();
    }

    @Test
    @DisplayName("Verify exact specification example: Trend 78.0, Momentum 64.0, RSI 71.0, Volume 83.0 -> Conviction 74.0")
    void testPromptSpecificationExample() {
        // Price = 2850.00.
        // For Trend = 78.0: 50 + (diffPct / 2.0) * 50 = 78.0 -> diffPct = +1.12%
        // SMA = 2850.00 / 1.0112 = 2818.4335
        double smaVal = 2850.00 / 1.0112;

        IndicatorResult trend = new IndicatorResult(smaVal, Signal.POSITIVE, true);
        IndicatorResult ema = new IndicatorResult(smaVal, Signal.POSITIVE, true);
        IndicatorResult rsi = new IndicatorResult(71.0, Signal.POSITIVE, true);
        IndicatorResult mom = new IndicatorResult(0.56, Signal.POSITIVE, true); // +0.56% -> 64.0
        IndicatorResult vol = new IndicatorResult(1.66, Signal.POSITIVE, true); // 1.66x -> 83.0

        ConvictionScore score = engine.evaluate(snapshot, trend, ema, rsi, mom, vol);

        assertTrue(score.ready());
        assertEquals(78.0, score.trendScore(), 0.1, "Trend score must be 78.0");
        assertEquals(64.0, score.momentumScore(), 0.1, "Momentum score must be 64.0");
        assertEquals(71.0, score.rsiScore(), 0.1, "RSI score must be 71.0");
        assertEquals(83.0, score.volumeScore(), 0.1, "Volume score must be 83.0");

        // Weighted contributions (0.25 * subScore)
        assertEquals(19.5, score.trendContribution(), 0.1, "Trend contribution: 0.25 * 78 = 19.5");
        assertEquals(16.0, score.momentumContribution(), 0.1, "Momentum contribution: 0.25 * 64 = 16.0");
        assertEquals(17.8, score.rsiContribution(), 0.1, "RSI contribution: 0.25 * 71 = 17.8");
        assertEquals(20.8, score.volumeContribution(), 0.1, "Volume contribution: 0.25 * 83 = 20.8");

        // Final score
        assertEquals(74.0, score.score(), 0.1, "Final composite conviction score must be exactly 74.0");
        assertEquals(ScoreCategory.STRONG, score.category());
    }

    @Test
    @DisplayName("Verify continuous linear progression without repeated 12.5 multiples")
    void testContinuousGradualProgression() {
        // Check small increments in RSI produce continuous changes, not jumps of 12.5
        IndicatorResult neutralSma = new IndicatorResult(2850.00, Signal.NEUTRAL, true);
        IndicatorResult neutralMom = new IndicatorResult(0.0, Signal.NEUTRAL, true);
        IndicatorResult neutralVol = new IndicatorResult(1.0, Signal.NEUTRAL, true);

        double previousScore = 0.0;
        for (double rsiVal = 40.0; rsiVal <= 65.0; rsiVal += 2.5) {
            IndicatorResult rsi = new IndicatorResult(rsiVal, Signal.NEUTRAL, true);
            ConvictionScore score = engine.evaluate(snapshot, neutralSma, neutralSma, rsi, neutralMom, neutralVol);

            // Factor score matches rsiVal
            assertEquals(rsiVal, score.rsiScore(), 0.1);
            // Trend=50, Mom=50, Vol=50 -> (50*0.25)*3 = 37.5 + (rsiVal * 0.25)
            double expectedScore = 37.5 + (rsiVal * 0.25);
            assertEquals(expectedScore, score.score(), 0.1);

            if (previousScore > 0.0) {
                // Confirm strictly continuous increments (~0.6 pts per 2.5 RSI step)
                assertTrue(score.score() > previousScore, "Score must increase smoothly and continuously");
            }
            previousScore = score.score();
        }
    }

    @Test
    @DisplayName("Verify clamping: extreme indicators do not exceed [0, 100]")
    void testExtremeClamping() {
        IndicatorResult extremeHighTrend = new IndicatorResult(100.0, Signal.POSITIVE, true); // price 2850 vs SMA 100 -> +2750%
        IndicatorResult extremeHighMom = new IndicatorResult(50.0, Signal.POSITIVE, true);    // +50% momentum
        IndicatorResult extremeHighRsi = new IndicatorResult(150.0, Signal.POSITIVE, true);   // 150 RSI
        IndicatorResult extremeHighVol = new IndicatorResult(10.0, Signal.POSITIVE, true);    // 10x RVOL

        ConvictionScore score = engine.evaluate(snapshot, extremeHighTrend, extremeHighTrend, extremeHighRsi, extremeHighMom, extremeHighVol);

        assertEquals(100.0, score.trendScore());
        assertEquals(100.0, score.momentumScore());
        assertEquals(100.0, score.rsiScore());
        assertEquals(100.0, score.volumeScore());
        assertEquals(100.0, score.score());
        assertEquals(ScoreCategory.VERY_STRONG, score.category());
    }
}
