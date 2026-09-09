package com.quantstream.backend.analytics.indicator;

import com.quantstream.backend.analytics.state.MarketState;
import com.quantstream.backend.domain.StockTick;
import com.quantstream.backend.domain.TickEventType;
import com.quantstream.backend.domain.TickSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class QuantitativeIndicatorsTest {

    private MarketState marketState;

    @BeforeEach
    void setUp() {
        marketState = new MarketState("TEST", 50);
    }

    private void feedTicks(double... prices) {
        for (double p : prices) {
            marketState.update(new StockTick(
                    UUID.randomUUID(),
                    "TEST",
                    BigDecimal.valueOf(p),
                    1000L,
                    Instant.now(),
                    TickEventType.TRADE,
                    TickSource.SIMULATION,
                    "NSE"
            ));
        }
    }

    private void feedVolumes(long... volumes) {
        for (long v : volumes) {
            marketState.update(new StockTick(
                    UUID.randomUUID(),
                    "TEST",
                    BigDecimal.valueOf(100.0),
                    v,
                    Instant.now(),
                    TickEventType.TRADE,
                    TickSource.SIMULATION,
                    "NSE"
            ));
        }
    }

    @Test
    void smaReturnsNotReadyDuringWarmup() {
        SmaIndicator sma = new SmaIndicator(5);
        feedTicks(10.0, 20.0, 30.0, 40.0); // only 4 ticks

        IndicatorResult result = sma.calculate(marketState.getSnapshot());
        assertFalse(result.ready());
        assertEquals(Signal.NOT_READY, result.signal());
    }

    @Test
    void smaCalculatesAccurateValueAndSignal() {
        SmaIndicator sma = new SmaIndicator(5);
        // Average of 10, 20, 30, 40, 50 is 30.0. Latest price is 50.0 (> 30.0 * 1.002)
        feedTicks(10.0, 20.0, 30.0, 40.0, 50.0);

        IndicatorResult result = sma.calculate(marketState.getSnapshot());
        assertTrue(result.ready());
        assertEquals(30.0, result.value(), 0.01);
        assertEquals(Signal.POSITIVE, result.signal());
    }

    @Test
    void emaCalculatesAccurately() {
        EmaIndicator ema = new EmaIndicator(3);
        feedTicks(10.0, 10.0, 10.0, 20.0);

        IndicatorResult result = ema.calculate(marketState.getSnapshot());
        assertTrue(result.ready());
        // Initial SMA of first 3 prices is 10.0.
        // Multiplier = 2 / (3 + 1) = 0.5.
        // Next price 20.0: EMA = 20 * 0.5 + 10 * 0.5 = 15.0
        assertEquals(15.0, result.value(), 0.01);
        assertEquals(Signal.POSITIVE, result.signal());
    }

    @Test
    void rsiDetectsOverboughtAndOversold() {
        RsiIndicator rsi = new RsiIndicator(5, 60.0, 40.0);

        // 5 period requires 6 prices. Strictly increasing prices:
        feedTicks(10.0, 12.0, 14.0, 16.0, 18.0, 20.0);
        IndicatorResult bullishResult = rsi.calculate(marketState.getSnapshot());
        assertTrue(bullishResult.ready());
        assertEquals(100.0, bullishResult.value(), 0.01);
        assertEquals(Signal.POSITIVE, bullishResult.signal());

        // Now test falling market
        MarketState bearState = new MarketState("BEAR", 50);
        for (double p : new double[]{50.0, 45.0, 40.0, 35.0, 30.0, 25.0}) {
            bearState.update(new StockTick(
                    UUID.randomUUID(), "BEAR", BigDecimal.valueOf(p), 1000L,
                    Instant.now(), TickEventType.TRADE, TickSource.SIMULATION, "NSE"
            ));
        }
        IndicatorResult bearishResult = rsi.calculate(bearState.getSnapshot());
        assertTrue(bearishResult.ready());
        assertEquals(0.0, bearishResult.value(), 0.01);
        assertEquals(Signal.NEGATIVE, bearishResult.signal());
    }

    @Test
    void momentumCalculatesPercentageChange() {
        MomentumIndicator momentum = new MomentumIndicator(3, 0.5, -0.5);
        // Requires 4 prices (t-3 to t)
        feedTicks(100.0, 102.0, 105.0, 110.0);

        IndicatorResult result = momentum.calculate(marketState.getSnapshot());
        assertTrue(result.ready());
        // Change from 100 to 110 is +10.0%
        assertEquals(10.0, result.value(), 0.01);
        assertEquals(Signal.POSITIVE, result.signal());
    }

    @Test
    void relativeVolumeCalculatesRatioToBaseline() {
        RelativeVolumeIndicator rvol = new RelativeVolumeIndicator(3, 1.5, 0.7);
        feedVolumes(1000L, 1000L, 4000L); // baseline avg = 2000, current = 4000 -> 2.0x

        IndicatorResult result = rvol.calculate(marketState.getSnapshot());
        assertTrue(result.ready());
        assertEquals(2.0, result.value(), 0.01);
        assertEquals(Signal.POSITIVE, result.signal());
    }

    @Test
    void rsiFlatPricesProducesNeutralFiftyScore() {
        RsiIndicator rsi = new RsiIndicator(5, 60.0, 40.0);
        // Completely flat prices across 6 periods: gain = 0, loss = 0 -> RS undefined -> RSI = 50.0 (Neutral)
        feedTicks(100.0, 100.0, 100.0, 100.0, 100.0, 100.0);

        IndicatorResult result = rsi.calculate(marketState.getSnapshot());
        assertTrue(result.ready());
        assertEquals(50.0, result.value(), 0.01);
        assertEquals(Signal.NEUTRAL, result.signal());
    }

    @Test
    void momentumZeroChangeProducesNeutralSignal() {
        MomentumIndicator momentum = new MomentumIndicator(3, 0.5, -0.5);
        feedTicks(100.0, 105.0, 95.0, 100.0); // t-3 was 100.0, t is 100.0 -> change = 0.0%

        IndicatorResult result = momentum.calculate(marketState.getSnapshot());
        assertTrue(result.ready());
        assertEquals(0.0, result.value(), 0.01);
        assertEquals(Signal.NEUTRAL, result.signal());
    }

    @Test
    void relativeVolumeZeroBaselineHandledSafely() {
        RelativeVolumeIndicator rvol = new RelativeVolumeIndicator(3, 1.5, 0.7);
        feedVolumes(0L, 0L, 0L); // All 0 volumes -> baseline is 0.0 -> cannot divide by 0 -> returns notReady

        IndicatorResult result = rvol.calculate(marketState.getSnapshot());
        assertFalse(result.ready());
        assertEquals(Signal.NOT_READY, result.signal());
    }

    @Test
    void smaPriceExactlyEqualToMovingAverageProducesNeutralSignal() {
        SmaIndicator sma = new SmaIndicator(5);
        feedTicks(100.0, 100.0, 100.0, 100.0, 100.0); // SMA = 100.0, Price = 100.0

        IndicatorResult result = sma.calculate(marketState.getSnapshot());
        assertTrue(result.ready());
        assertEquals(100.0, result.value(), 0.01);
        assertEquals(Signal.NEUTRAL, result.signal());
    }

    @Test
    void smaPriceWithinThresholdBandProducesNeutralSignal() {
        SmaIndicator sma = new SmaIndicator(5);
        // SMA of 100, 100, 100, 100, 100.10 is ~100.02.
        // Band is +/- 0.2%: [99.82, 100.22]. Latest price 100.10 is within band -> NEUTRAL
        feedTicks(100.0, 100.0, 100.0, 100.0, 100.10);

        IndicatorResult result = sma.calculate(marketState.getSnapshot());
        assertTrue(result.ready());
        assertEquals(100.02, result.value(), 0.01);
        assertEquals(Signal.NEUTRAL, result.signal());
    }
}
