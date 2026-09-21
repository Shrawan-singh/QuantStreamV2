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
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Continuous Conviction Scoring Granularity & Explainability Test")
class ConvictionScoreEngineContinuousTest {

    private ConvictionScoreEngine engine;
    private MarketState.Snapshot snapshot;
    private double measuredVol;

    @BeforeEach
    void setUp() {
        // Equal 25% weights, alpha=1.0 for testing raw continuous response without lag
        engine = new ConvictionScoreEngine(new ScoringProperties(
                25.0, 25.0, 25.0, 25.0,
                20, 5, 3.0, 4.0, 3.0, 1.0, 1.5
        ));

        // Create 20-period price history with alternating oscillation
        MarketState state = new MarketState("RELIANCE", 20);
        for (int i = 0; i < 20; i++) {
            double price = 2850.00 * (1.0 + (i % 2 == 0 ? 0.007 : -0.007));
            state.update(new StockTick(
                    UUID.randomUUID(),
                    "RELIANCE",
                    BigDecimal.valueOf(price).setScale(2, RoundingMode.HALF_UP),
                    500_000L,
                    Instant.now(),
                    TickEventType.TRADE,
                    TickSource.SIMULATION,
                    "NSE"
            ));
        }
        snapshot = state.getSnapshot();
        measuredVol = ConvictionScoreEngine.calculateRealizedVolatility(snapshot.recentPrices(), 20, 5);
        assertTrue(measuredVol > 0.0);
    }

    @Test
    @DisplayName("Verify volatility-relative scoring: Trend 78.0, Momentum 64.0, RSI 71.0, Volume 83.0 -> Conviction 74.0")
    void testPromptSpecificationExample() {
        double currentPrice = snapshot.latestPrice().doubleValue();

        // For Trend: target trendBase = 74.0, + 4.0 EMA bonus = 78.0
        // (Z / 3.0) * 50 = 24.0 -> Z = 1.44 -> divergence = 1.44 * measuredVol
        double trendZ = 1.44;
        double targetDiv = trendZ * measuredVol;
        double smaVal = currentPrice / (1.0 + targetDiv);
        double emaVal = smaVal;

        // For Momentum: target momentumScore = 64.0 -> (Z / 3.0) * 50 = 14.0 -> Z = 0.84
        double momZ = 0.84;
        double momPct = momZ * measuredVol * 100.0;

        IndicatorResult trend = new IndicatorResult(smaVal, Signal.POSITIVE, true);
        IndicatorResult ema = new IndicatorResult(emaVal, Signal.POSITIVE, true);
        IndicatorResult rsi = new IndicatorResult(71.0, Signal.POSITIVE, true);
        IndicatorResult mom = new IndicatorResult(momPct, Signal.POSITIVE, true);
        IndicatorResult vol = new IndicatorResult(1.66, Signal.POSITIVE, true); // 1.66x -> 83.0

        ConvictionScore score = engine.evaluate(snapshot, trend, ema, rsi, mom, vol);

        assertTrue(score.ready());
        assertEquals(78.0, score.trendScore(), 0.5, "Trend score must be ~78.0");
        assertEquals(64.0, score.momentumScore(), 0.5, "Momentum score must be ~64.0");
        assertEquals(71.0, score.rsiScore(), 0.1, "RSI score must be 71.0");
        assertEquals(83.0, score.volumeScore(), 0.1, "Volume score must be 83.0");

        // Weighted contributions (0.25 * subScore)
        assertEquals(19.5, score.trendContribution(), 0.2, "Trend contribution: 0.25 * 78 = 19.5");
        assertEquals(16.0, score.momentumContribution(), 0.2, "Momentum contribution: 0.25 * 64 = 16.0");
        assertEquals(17.8, score.rsiContribution(), 0.1, "RSI contribution: 0.25 * 71 = 17.8");
        assertEquals(20.8, score.volumeContribution(), 0.1, "Volume contribution: 0.25 * 83 = 20.8");

        // Final score
        assertEquals(74.0, score.score(), 0.3, "Final composite conviction score must be ~74.0");
        assertEquals(ScoreCategory.STRONG, score.category());
    }

    @Test
    @DisplayName("Verify continuous linear progression without repeated 12.5 multiples")
    void testContinuousGradualProgression() {
        double currentPrice = snapshot.latestPrice().doubleValue();
        IndicatorResult neutralSma = new IndicatorResult(currentPrice, Signal.NEUTRAL, true);
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
        double currentPrice = snapshot.latestPrice().doubleValue();
        // Sma far below price: price > 5 * SMA -> Z >> 3.0 -> trendBase = 100.0
        IndicatorResult extremeHighTrend = new IndicatorResult(currentPrice / 2.0, Signal.POSITIVE, true);
        IndicatorResult extremeHighMom = new IndicatorResult(50.0, Signal.POSITIVE, true);
        IndicatorResult extremeHighRsi = new IndicatorResult(150.0, Signal.POSITIVE, true);
        IndicatorResult extremeHighVol = new IndicatorResult(10.0, Signal.POSITIVE, true);

        ConvictionScore score = engine.evaluate(snapshot, extremeHighTrend, extremeHighTrend, extremeHighRsi, extremeHighMom, extremeHighVol);

        assertEquals(100.0, score.trendScore());
        assertEquals(100.0, score.momentumScore());
        assertEquals(100.0, score.rsiScore());
        assertEquals(100.0, score.volumeScore());
        assertEquals(100.0, score.score());
        assertEquals(ScoreCategory.VERY_STRONG, score.category());
    }
}
