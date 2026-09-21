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

class ConvictionScoreEngineTest {

    private ConvictionScoreEngine engine;
    private MarketState.Snapshot standardSnapshot;

    @BeforeEach
    void setUp() {
        engine = new ConvictionScoreEngine(new ScoringProperties(
                25.0, 25.0, 25.0, 25.0, // weights
                20, 5, 3.0, 4.0, 3.0, 1.0, 1.5 // alpha=1.0 for unit testing raw mapping
        ));

        // Create 20-tick snapshot with ~1% realized return volatility
        standardSnapshot = createSnapshotWithVolatility("RELIANCE", 2500.0, 0.007, 20);
    }

    private MarketState.Snapshot createSnapshotWithVolatility(String symbol, double basePrice, double oscillationPct, int count) {
        MarketState state = new MarketState(symbol, count);
        for (int i = 0; i < count; i++) {
            double price = basePrice * (1.0 + (i % 2 == 0 ? oscillationPct : -oscillationPct));
            state.update(new StockTick(
                    UUID.randomUUID(),
                    symbol,
                    BigDecimal.valueOf(price).setScale(2, RoundingMode.HALF_UP),
                    1000L,
                    Instant.now(),
                    TickEventType.TRADE,
                    TickSource.SIMULATION,
                    "NSE"
            ));
        }
        return state.getSnapshot();
    }

    @Test
    @DisplayName("All indicators not ready produces default neutral score")
    void allIndicatorsNotReadyProducesDefaultNeutralScore() {
        ConvictionScore score = engine.evaluate(
                standardSnapshot,
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
    @DisplayName("Insufficient price history for volatility preserves NOT_READY for trend and momentum")
    void insufficientHistoryHandledSafely() {
        MarketState state = new MarketState("INFY", 20);
        state.update(new StockTick(
                UUID.randomUUID(), "INFY", BigDecimal.valueOf(1500.0), 1000L,
                Instant.now(), TickEventType.TRADE, TickSource.SIMULATION, "NSE"
        ));
        MarketState.Snapshot shortSnapshot = state.getSnapshot();

        IndicatorResult trend = new IndicatorResult(1480.0, Signal.POSITIVE, true);
        IndicatorResult ema = new IndicatorResult(1480.0, Signal.POSITIVE, true);
        IndicatorResult rsi = new IndicatorResult(70.0, Signal.POSITIVE, true);
        IndicatorResult mom = new IndicatorResult(1.5, Signal.POSITIVE, true);
        IndicatorResult vol = new IndicatorResult(1.5, Signal.POSITIVE, true);

        ConvictionScore score = engine.evaluate(shortSnapshot, trend, ema, rsi, mom, vol);

        assertTrue(score.ready());
        // Trend and momentum remain neutral (50.0) awaiting volatility history
        assertEquals(50.0, score.trendScore());
        assertEquals(50.0, score.momentumScore());
        assertEquals(70.0, score.rsiScore());
        assertEquals(75.0, score.volumeScore());
        assertTrue(score.explanations().stream().anyMatch(e -> e.contains("awaiting")));
    }

    @Test
    @DisplayName("Zero volatility (flat price window) handles neutral baseline without division by zero")
    void zeroVolatilityConstantPricesHandledSafely() {
        MarketState state = new MarketState("TCS", 20);
        for (int i = 0; i < 20; i++) {
            state.update(new StockTick(
                    UUID.randomUUID(), "TCS", BigDecimal.valueOf(3500.0), 1000L,
                    Instant.now(), TickEventType.TRADE, TickSource.SIMULATION, "NSE"
            ));
        }
        MarketState.Snapshot flatSnapshot = state.getSnapshot();

        IndicatorResult sma = new IndicatorResult(3500.0, Signal.NEUTRAL, true);
        IndicatorResult ema = new IndicatorResult(3500.0, Signal.NEUTRAL, true);
        IndicatorResult rsi = new IndicatorResult(50.0, Signal.NEUTRAL, true);
        IndicatorResult mom = new IndicatorResult(0.0, Signal.NEUTRAL, true);
        IndicatorResult vol = new IndicatorResult(1.0, Signal.NEUTRAL, true);

        ConvictionScore score = engine.evaluate(flatSnapshot, sma, ema, rsi, mom, vol);

        assertTrue(score.ready());
        assertEquals(50.0, score.score());
        assertEquals(50.0, score.trendScore());
        assertEquals(50.0, score.momentumScore());
        assertEquals(ScoreCategory.NEUTRAL, score.category());
        assertEquals(0.0, score.realizedVolatility(), 1e-4);
    }

    @Test
    @DisplayName("All maximum indicators produce 100 score and VERY_STRONG category")
    void allMaxIndicatorsProduce100ScoreAndVeryStrongCategory() {
        double vol = ConvictionScoreEngine.calculateRealizedVolatility(standardSnapshot.recentPrices(), 20, 5);
        assertTrue(vol > 0.0);

        // Price is +3.5 sigma above SMA (divergence > 3.0 * vol)
        double currentPrice = standardSnapshot.latestPrice().doubleValue();
        double smaVal = currentPrice / (1.0 + (3.5 * vol));
        double emaVal = smaVal;

        IndicatorResult trend = new IndicatorResult(smaVal, Signal.POSITIVE, true);
        IndicatorResult ema = new IndicatorResult(emaVal, Signal.POSITIVE, true);
        IndicatorResult rsi = new IndicatorResult(100.0, Signal.POSITIVE, true);
        IndicatorResult mom = new IndicatorResult(3.5 * vol * 100.0, Signal.POSITIVE, true); // +3.5 sigma
        IndicatorResult rvol = new IndicatorResult(2.0, Signal.POSITIVE, true); // 2.0x -> 100.0

        ConvictionScore score = engine.evaluate(standardSnapshot, trend, ema, rsi, mom, rvol);

        assertTrue(score.ready());
        assertEquals(100.0, score.trendScore());
        assertEquals(100.0, score.momentumScore());
        assertEquals(100.0, score.rsiScore());
        assertEquals(100.0, score.volumeScore());
        assertEquals(25.0, score.trendContribution());
        assertEquals(25.0, score.momentumContribution());
        assertEquals(25.0, score.rsiContribution());
        assertEquals(25.0, score.volumeContribution());
        assertEquals(100.0, score.score());
        assertEquals(ScoreCategory.VERY_STRONG, score.category());
    }

    @Test
    @DisplayName("All minimum indicators produce 0 score and VERY_WEAK category")
    void allMinIndicatorsProduceZeroScoreAndVeryWeakCategory() {
        double vol = ConvictionScoreEngine.calculateRealizedVolatility(standardSnapshot.recentPrices(), 20, 5);
        assertTrue(vol > 0.0);

        // Price is -3.5 sigma below SMA
        double currentPrice = standardSnapshot.latestPrice().doubleValue();
        double smaVal = currentPrice / (1.0 - (3.5 * vol));
        double emaVal = smaVal;

        IndicatorResult trend = new IndicatorResult(smaVal, Signal.NEGATIVE, true);
        IndicatorResult ema = new IndicatorResult(emaVal, Signal.NEGATIVE, true);
        IndicatorResult rsi = new IndicatorResult(0.0, Signal.NEGATIVE, true);
        IndicatorResult mom = new IndicatorResult(-3.5 * vol * 100.0, Signal.NEGATIVE, true); // -3.5 sigma
        IndicatorResult rvol = new IndicatorResult(0.0, Signal.NEGATIVE, true); // 0.0x -> 0.0

        ConvictionScore score = engine.evaluate(standardSnapshot, trend, ema, rsi, mom, rvol);

        assertTrue(score.ready());
        assertEquals(0.0, score.trendScore());
        assertEquals(0.0, score.momentumScore());
        assertEquals(0.0, score.rsiScore());
        assertEquals(0.0, score.volumeScore());
        assertEquals(0.0, score.trendContribution());
        assertEquals(0.0, score.momentumContribution());
        assertEquals(0.0, score.rsiContribution());
        assertEquals(0.0, score.volumeContribution());
        assertEquals(0.0, score.score());
        assertEquals(ScoreCategory.VERY_WEAK, score.category());
    }

    @Test
    @DisplayName("EMA confirmation adds positive bonus on agreement and penalty on divergence")
    void emaConfirmationValidation() {
        double vol = ConvictionScoreEngine.calculateRealizedVolatility(standardSnapshot.recentPrices(), 20, 5);
        double currentPrice = standardSnapshot.latestPrice().doubleValue();

        // 1. Both agree bullish (Price > SMA and Price > EMA)
        double smaVal = currentPrice / (1.0 + (1.5 * vol)); // +1.5 sigma -> base 75.0
        double emaVal = smaVal;
        IndicatorResult smaBullish = new IndicatorResult(smaVal, Signal.POSITIVE, true);
        IndicatorResult emaBullish = new IndicatorResult(emaVal, Signal.POSITIVE, true);
        IndicatorResult neutral = new IndicatorResult(50.0, Signal.NEUTRAL, true);
        IndicatorResult neutralMom = new IndicatorResult(0.0, Signal.NEUTRAL, true);
        IndicatorResult neutralVol = new IndicatorResult(1.0, Signal.NEUTRAL, true);

        ConvictionScore scoreBothBullish = engine.evaluate(standardSnapshot, smaBullish, emaBullish, neutral, neutralMom, neutralVol);
        // Base 75.0 + 4.0 EMA confirmation bonus = 79.0
        assertEquals(79.0, scoreBothBullish.trendScore(), 0.5);
        assertTrue(scoreBothBullish.explanations().stream().anyMatch(e -> e.contains("agree bullish")));

        // 2. Both agree bearish (Price < SMA and Price < EMA)
        double smaBearVal = currentPrice / (1.0 - (1.5 * vol)); // -1.5 sigma -> base 25.0
        double emaBearVal = smaBearVal;
        IndicatorResult smaBearish = new IndicatorResult(smaBearVal, Signal.NEGATIVE, true);
        IndicatorResult emaBearish = new IndicatorResult(emaBearVal, Signal.NEGATIVE, true);

        ConvictionScore scoreBothBearish = engine.evaluate(standardSnapshot, smaBearish, emaBearish, neutral, neutralMom, neutralVol);
        // Base 25.0 - 4.0 EMA confirmation penalty = 21.0
        assertEquals(21.0, scoreBothBearish.trendScore(), 0.5);
        assertTrue(scoreBothBearish.explanations().stream().anyMatch(e -> e.contains("agree bearish")));

        // 3. Divergence: Price > SMA but Price < EMA
        IndicatorResult emaHigher = new IndicatorResult(currentPrice * 1.05, Signal.NEGATIVE, true);
        ConvictionScore scoreDivergent = engine.evaluate(standardSnapshot, smaBullish, emaHigher, neutral, neutralMom, neutralVol);
        // Base 75.0 - 3.0 divergence penalty = 72.0
        assertEquals(72.0, scoreDivergent.trendScore(), 0.5);
        assertTrue(scoreDivergent.explanations().stream().anyMatch(e -> e.contains("diverge")));
    }

    @Test
    @DisplayName("Score smoothing and category hysteresis prevent rapid boundary flipping")
    void scoreSmoothingAndHysteresis() {
        ConvictionScoreEngine smoothingEngine = new ConvictionScoreEngine(new ScoringProperties(
                25.0, 25.0, 25.0, 25.0,
                20, 5, 3.0, 4.0, 3.0,
                0.20, // alpha = 0.20
                1.5   // hysteresis = 1.5
        ));

        // Start with a state in STRONG category (score = 70.0)
        assertEquals(ScoreCategory.STRONG,
                ConvictionScoreEngine.calculateCategoryWithHysteresis(70.0, null, 1.5));

        // Score dips to 59.0 (below 60.0 boundary, but NOT below 60.0 - 1.5 = 58.5)
        // With hysteresis, it must REMAIN in STRONG!
        ScoreCategory heldCategory = ConvictionScoreEngine.calculateCategoryWithHysteresis(
                59.0, ScoreCategory.STRONG, 1.5
        );
        assertEquals(ScoreCategory.STRONG, heldCategory, "Score 59.0 must remain STRONG due to 1.5 hysteresis");

        // Score drops further to 58.0 (< 58.5) -> now transitions to NEUTRAL
        ScoreCategory droppedCategory = ConvictionScoreEngine.calculateCategoryWithHysteresis(
                58.0, ScoreCategory.STRONG, 1.5
        );
        assertEquals(ScoreCategory.NEUTRAL, droppedCategory, "Score 58.0 must transition to NEUTRAL");

        // Now from NEUTRAL, score rises to 60.5 (above 60.0, but NOT above 60.0 + 1.5 = 61.5)
        ScoreCategory heldNeutral = ConvictionScoreEngine.calculateCategoryWithHysteresis(
                60.5, ScoreCategory.NEUTRAL, 1.5
        );
        assertEquals(ScoreCategory.NEUTRAL, heldNeutral, "Score 60.5 must remain NEUTRAL until crossing 61.5");

        // Score rises to 62.0 (> 61.5) -> transitions to STRONG
        ScoreCategory risenCategory = ConvictionScoreEngine.calculateCategoryWithHysteresis(
                62.0, ScoreCategory.NEUTRAL, 1.5
        );
        assertEquals(ScoreCategory.STRONG, risenCategory, "Score 62.0 must transition to STRONG");

        // Test boundary around 40.0:
        // From NEUTRAL, dipping to 39.0 (>= 38.5) stays NEUTRAL
        assertEquals(ScoreCategory.NEUTRAL,
                ConvictionScoreEngine.calculateCategoryWithHysteresis(39.0, ScoreCategory.NEUTRAL, 1.5));
        // Dipping to 38.0 (< 38.5) transitions to WEAK
        assertEquals(ScoreCategory.WEAK,
                ConvictionScoreEngine.calculateCategoryWithHysteresis(38.0, ScoreCategory.NEUTRAL, 1.5));
        // From WEAK, rising to 41.0 (<= 41.5) stays WEAK
        assertEquals(ScoreCategory.WEAK,
                ConvictionScoreEngine.calculateCategoryWithHysteresis(41.0, ScoreCategory.WEAK, 1.5));
        // Rising to 42.0 (> 41.5) transitions to NEUTRAL
        assertEquals(ScoreCategory.NEUTRAL,
                ConvictionScoreEngine.calculateCategoryWithHysteresis(42.0, ScoreCategory.WEAK, 1.5));
    }

    @Test
    @DisplayName("Cross-Volatility: Both low-volatility and high-volatility symbols reach 0 and 100 extremes")
    void crossVolatilityExtremeReachability() {
        // 1. Low-volatility stock (0.5% return oscillation)
        MarketState.Snapshot lowVolSnapshot = createSnapshotWithVolatility("LOWVOL", 1000.0, 0.0035, 20);
        double lowVol = ConvictionScoreEngine.calculateRealizedVolatility(lowVolSnapshot.recentPrices(), 20, 5);
        assertTrue(lowVol < 0.010, "Expected low volatility < 1.0%");

        // 2. High-volatility stock (5.0% return oscillation)
        MarketState.Snapshot highVolSnapshot = createSnapshotWithVolatility("HIGHVOL", 1000.0, 0.035, 20);
        double highVol = ConvictionScoreEngine.calculateRealizedVolatility(highVolSnapshot.recentPrices(), 20, 5);
        assertTrue(highVol > 0.025, "Expected high volatility > 2.5%");

        // Both stocks pushed to +3.5 sigma
        double lowPrice = lowVolSnapshot.latestPrice().doubleValue();
        double lowSma = lowPrice / (1.0 + (3.5 * lowVol));
        IndicatorResult lowTrend = new IndicatorResult(lowSma, Signal.POSITIVE, true);
        IndicatorResult lowMom = new IndicatorResult(3.5 * lowVol * 100.0, Signal.POSITIVE, true);

        double highPrice = highVolSnapshot.latestPrice().doubleValue();
        double highSma = highPrice / (1.0 + (3.5 * highVol));
        IndicatorResult highTrend = new IndicatorResult(highSma, Signal.POSITIVE, true);
        IndicatorResult highMom = new IndicatorResult(3.5 * highVol * 100.0, Signal.POSITIVE, true);

        IndicatorResult maxRsi = new IndicatorResult(100.0, Signal.POSITIVE, true);
        IndicatorResult maxVol = new IndicatorResult(2.0, Signal.POSITIVE, true);

        ConvictionScore lowScore = engine.evaluate(lowVolSnapshot, lowTrend, lowTrend, maxRsi, lowMom, maxVol);
        ConvictionScore highScore = engine.evaluate(highVolSnapshot, highTrend, highTrend, maxRsi, highMom, maxVol);

        // Prove BOTH reach 100.0 extreme despite vastly different absolute volatilities!
        assertEquals(100.0, lowScore.score(), "Low volatility symbol must reach 100.0 at +3.5 sigma");
        assertEquals(100.0, highScore.score(), "High volatility symbol must reach 100.0 at +3.5 sigma");

        // Both stocks pushed to -3.5 sigma
        double lowSmaBear = lowPrice / (1.0 - (3.5 * lowVol));
        double highSmaBear = highPrice / (1.0 - (3.5 * highVol));
        IndicatorResult lowTrendBear = new IndicatorResult(lowSmaBear, Signal.NEGATIVE, true);
        IndicatorResult lowMomBear = new IndicatorResult(-3.5 * lowVol * 100.0, Signal.NEGATIVE, true);
        IndicatorResult highTrendBear = new IndicatorResult(highSmaBear, Signal.NEGATIVE, true);
        IndicatorResult highMomBear = new IndicatorResult(-3.5 * highVol * 100.0, Signal.NEGATIVE, true);
        IndicatorResult minRsi = new IndicatorResult(0.0, Signal.NEGATIVE, true);
        IndicatorResult minVol = new IndicatorResult(0.0, Signal.NEGATIVE, true);

        ConvictionScore lowMinScore = engine.evaluate(lowVolSnapshot, lowTrendBear, lowTrendBear, minRsi, lowMomBear, minVol);
        ConvictionScore highMinScore = engine.evaluate(highVolSnapshot, highTrendBear, highTrendBear, minRsi, highMomBear, minVol);

        // Prove BOTH reach 0.0 extreme!
        assertEquals(0.0, lowMinScore.score(), "Low volatility symbol must reach 0.0 at -3.5 sigma");
        assertEquals(0.0, highMinScore.score(), "High volatility symbol must reach 0.0 at -3.5 sigma");
    }

    @Test
    @DisplayName("Scores are strictly clamped to [0.0, 100.0] under extreme values")
    void scoreClampingGuarantees() {
        IndicatorResult extremeTrendPos = new IndicatorResult(1.0, Signal.POSITIVE, true); // Price 2500 >> 1.0
        IndicatorResult extremeTrendNeg = new IndicatorResult(999999.0, Signal.NEGATIVE, true); // Price 2500 << 999999.0
        IndicatorResult extremePos = new IndicatorResult(9999.0, Signal.POSITIVE, true);
        IndicatorResult extremeNeg = new IndicatorResult(-9999.0, Signal.NEGATIVE, true);

        ConvictionScore max = engine.evaluate(standardSnapshot, extremeTrendPos, extremeTrendPos, extremePos, extremePos, extremePos);
        assertTrue(max.score() <= 100.0);
        assertEquals(100.0, max.score());

        ConvictionScore min = engine.evaluate(standardSnapshot, extremeTrendNeg, extremeTrendNeg, extremeNeg, extremeNeg, extremeNeg);
        assertTrue(min.score() >= 0.0);
        assertEquals(0.0, min.score());
    }
}
