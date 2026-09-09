package com.quantstream.backend.benchmark;

import com.quantstream.backend.analytics.AnalyticsEngine;
import com.quantstream.backend.analytics.indicator.EmaIndicator;
import com.quantstream.backend.analytics.indicator.MomentumIndicator;
import com.quantstream.backend.analytics.indicator.RelativeVolumeIndicator;
import com.quantstream.backend.analytics.indicator.RsiIndicator;
import com.quantstream.backend.analytics.indicator.SmaIndicator;
import com.quantstream.backend.analytics.scoring.ConvictionScoreEngine;
import com.quantstream.backend.analytics.state.MarketStateStore;
import com.quantstream.backend.config.IndicatorProperties;
import com.quantstream.backend.config.ScoringProperties;
import com.quantstream.backend.domain.StockTick;
import com.quantstream.backend.marketdata.simulation.DeterministicTickGenerator;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Repeatable, measured quantitative analytics performance benchmark.
 *
 * <p>Instruments nanosecond-resolution timings across state update, indicator computations,
 * conviction score evaluation, and composite pipeline throughput.</p>
 */
class EnginePerformanceBenchmarkTest {

    private static final int WARMUP_TICKS = 1_000;
    private static final int BENCHMARK_TICKS = 10_000;

    @Test
    void benchmarkQuantitativeAnalyticsPipeline() {
        IndicatorProperties indicatorProps = IndicatorProperties.defaultProperties();
        ScoringProperties scoringProps = ScoringProperties.defaultProperties();

        MarketStateStore store = new MarketStateStore(100);
        SmaIndicator sma = new SmaIndicator(indicatorProps);
        EmaIndicator ema = new EmaIndicator(indicatorProps);
        RsiIndicator rsi = new RsiIndicator(indicatorProps);
        MomentumIndicator mom = new MomentumIndicator(indicatorProps);
        RelativeVolumeIndicator rvol = new RelativeVolumeIndicator(indicatorProps);
        ConvictionScoreEngine scoreEngine = new ConvictionScoreEngine(scoringProps);

        AnalyticsEngine engine = new AnalyticsEngine(store, sma, ema, rsi, mom, rvol, scoreEngine);

        List<String> symbols = List.of("RELIANCE", "TCS", "INFY", "HDFCBANK", "ICICIBANK");
        DeterministicTickGenerator generator = new DeterministicTickGenerator(42L, symbols);

        // 1. Warm-up Phase (ensure JIT compilation)
        for (int i = 0; i < WARMUP_TICKS; i++) {
            String sym = symbols.get(i % symbols.size());
            engine.process(generator.nextTick(sym));
        }

        // 2. Measurement Phase
        long[] totalLatenciesNanos = new long[BENCHMARK_TICKS];
        long[] stateLatenciesNanos = new long[BENCHMARK_TICKS];
        long[] indicatorLatenciesNanos = new long[BENCHMARK_TICKS];
        long[] scoreLatenciesNanos = new long[BENCHMARK_TICKS];

        long benchmarkStart = System.nanoTime();

        for (int i = 0; i < BENCHMARK_TICKS; i++) {
            String sym = symbols.get(i % symbols.size());
            StockTick tick = generator.nextTick(sym);

            long t0 = System.nanoTime();

            // Step A: State update
            var snap = store.update(tick);
            long t1 = System.nanoTime();

            // Step B: Indicators
            var smaRes = sma.calculate(snap);
            var emaRes = ema.calculate(snap);
            var rsiRes = rsi.calculate(snap);
            var momRes = mom.calculate(snap);
            var rvolRes = rvol.calculate(snap);
            long t2 = System.nanoTime();

            // Step C: Scoring
            scoreEngine.evaluate(snap, smaRes, emaRes, rsiRes, momRes, rvolRes);
            long t3 = System.nanoTime();

            stateLatenciesNanos[i] = t1 - t0;
            indicatorLatenciesNanos[i] = t2 - t1;
            scoreLatenciesNanos[i] = t3 - t2;
            totalLatenciesNanos[i] = t3 - t0;
        }

        long benchmarkEnd = System.nanoTime();
        double totalDurationSeconds = (benchmarkEnd - benchmarkStart) / 1_000_000_000.0;
        double throughput = BENCHMARK_TICKS / totalDurationSeconds;

        Arrays.sort(totalLatenciesNanos);
        Arrays.sort(stateLatenciesNanos);
        Arrays.sort(indicatorLatenciesNanos);
        Arrays.sort(scoreLatenciesNanos);

        double p50Micros = totalLatenciesNanos[(int) (BENCHMARK_TICKS * 0.50)] / 1_000.0;
        double p95Micros = totalLatenciesNanos[(int) (BENCHMARK_TICKS * 0.95)] / 1_000.0;
        double p99Micros = totalLatenciesNanos[(int) (BENCHMARK_TICKS * 0.99)] / 1_000.0;
        double maxMicros = totalLatenciesNanos[BENCHMARK_TICKS - 1] / 1_000.0;

        double stateP50 = stateLatenciesNanos[(int) (BENCHMARK_TICKS * 0.50)] / 1_000.0;
        double indicatorP50 = indicatorLatenciesNanos[(int) (BENCHMARK_TICKS * 0.50)] / 1_000.0;
        double scoreP50 = scoreLatenciesNanos[(int) (BENCHMARK_TICKS * 0.50)] / 1_000.0;

        System.out.println("\n=======================================================");
        System.out.println("  LOCAL SYNTHETIC ANALYTICS ENGINE BENCHMARK           ");
        System.out.println("=======================================================");
        System.out.printf("Processed Ticks:                          %,d\n", BENCHMARK_TICKS);
        System.out.printf("Total Duration:                           %.3f seconds\n", totalDurationSeconds);
        System.out.printf("Throughput:                               %,.1f ticks/second\n", throughput);
        System.out.println("-------------------------------------------------------");
        System.out.printf("State Store Update (p50):                 %.2f µs\n", stateP50);
        System.out.printf("Indicator Calc (5x) (p50):                %.2f µs\n", indicatorP50);
        System.out.printf("Conviction Score Calc (p50):              %.2f µs\n", scoreP50);
        System.out.println("-------------------------------------------------------");
        System.out.printf("Engine Synthetic Pipeline Latency (p50):  %.2f µs\n", p50Micros);
        System.out.printf("Engine Synthetic Pipeline Latency (p95):  %.2f µs\n", p95Micros);
        System.out.printf("Engine Synthetic Pipeline Latency (p99):  %.2f µs\n", p99Micros);
        System.out.printf("Max Latency:                              %.2f µs\n", maxMicros);
        System.out.println("=======================================================\n");

        assertTrue(throughput > 5_000, "Throughput should exceed 5,000 ticks/second");
        assertTrue(p95Micros < 1000.0, "p95 latency should be sub-millisecond");
    }
}
