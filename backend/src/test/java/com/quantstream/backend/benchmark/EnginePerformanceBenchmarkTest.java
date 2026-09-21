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
import com.quantstream.backend.config.StreamingProperties;
import com.quantstream.backend.domain.InstrumentRegistry;
import com.quantstream.backend.domain.StockTick;
import com.quantstream.backend.marketdata.simulation.DeterministicTickGenerator;
import com.quantstream.backend.processing.TickProcessingService;
import com.quantstream.backend.processing.TickQueueService;
import com.quantstream.backend.processing.TickValidationService;
import com.quantstream.backend.processing.TickWorkerPool;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Repeatable, measured quantitative analytics and worker queue performance benchmark
 * across the expanded ~240 NSE simulation universe.
 */
class EnginePerformanceBenchmarkTest {

    private static final int WARMUP_TICKS = 1_000;
    private static final int BENCHMARK_TICKS = 10_000;

    @Test
    @DisplayName("Benchmark analytics engine across 233-symbol expanded simulation universe")
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

        List<String> symbols = InstrumentRegistry.getSimulationSymbols();
        assertTrue(symbols.size() >= 230, "Simulation universe should contain ~240 symbols");
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
        System.out.println("  QUANTSTREAM 233-SYMBOL ANALYTICS PIPELINE BENCHMARK  ");
        System.out.println("=======================================================");
        System.out.printf("Universe Symbols:                         %,d NSE Equities\n", symbols.size());
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

    @Test
    @DisplayName("Benchmark queue utilization, backpressure, and worker throughput for 233 symbols")
    void benchmarkExpandedUniverseQueueWorkerThroughput() throws Exception {
        int workerPoolSize = 4;
        int queueCapacity = 1000;
        int tickCount = 10_000;

        StreamingProperties streamingProps = new StreamingProperties();
        streamingProps.setWorkerPoolSize(workerPoolSize);
        streamingProps.setQueueSize(queueCapacity);
        streamingProps.setWorkerPollTimeoutMs(50L);

        TickQueueService queueService = new TickQueueService(streamingProps);

        IndicatorProperties indicatorProps = IndicatorProperties.defaultProperties();
        ScoringProperties scoringProps = ScoringProperties.defaultProperties();
        MarketStateStore store = new MarketStateStore(100);
        AnalyticsEngine analyticsEngine = new AnalyticsEngine(
                store,
                new SmaIndicator(indicatorProps),
                new EmaIndicator(indicatorProps),
                new RsiIndicator(indicatorProps),
                new MomentumIndicator(indicatorProps),
                new RelativeVolumeIndicator(indicatorProps),
                new ConvictionScoreEngine(scoringProps)
        );

        CountDownLatch latch = new CountDownLatch(tickCount);
        long[] processingLatenciesNanos = new long[tickCount];
        AtomicInteger processedCounter = new AtomicInteger(0);
        AtomicInteger maxQueueDepth = new AtomicInteger(0);
        java.util.concurrent.ConcurrentHashMap<java.util.UUID, Long> enqueueTimes = new java.util.concurrent.ConcurrentHashMap<>();

        TickProcessingService processingService = new TickProcessingService(new TickValidationService(), analyticsEngine) {
            @Override
            public void process(StockTick tick) {
                int currentDepth = queueService.size();
                maxQueueDepth.accumulateAndGet(currentDepth, Math::max);

                Long t0 = enqueueTimes.remove(tick.id());
                long latency = t0 != null ? (System.nanoTime() - t0) : 0L;

                super.process(tick);

                int idx = processedCounter.getAndIncrement();
                if (idx < tickCount) {
                    processingLatenciesNanos[idx] = Math.max(0L, latency);
                }
                latch.countDown();
            }
        };

        TickWorkerPool workerPool = new TickWorkerPool(streamingProps, queueService, processingService);
        workerPool.start();

        List<String> symbols = InstrumentRegistry.getSimulationSymbols();
        DeterministicTickGenerator generator = new DeterministicTickGenerator(12345L, symbols);

        long genStart = System.nanoTime();
        int backpressureHits = 0;

        for (int i = 0; i < tickCount; i++) {
            String sym = symbols.get(i % symbols.size());
            StockTick tick = generator.nextTick(sym);

            if (queueService.size() >= queueCapacity - 10) {
                backpressureHits++;
            }
            enqueueTimes.put(tick.id(), System.nanoTime());
            queueService.enqueue(tick);
        }
        long genEnd = System.nanoTime();
        double genDurationSec = (genEnd - genStart) / 1_000_000_000.0;
        double genRate = tickCount / genDurationSec;

        boolean completed = latch.await(10, TimeUnit.SECONDS);
        long procEnd = System.nanoTime();
        double procDurationSec = (procEnd - genStart) / 1_000_000_000.0;
        double procRate = tickCount / procDurationSec;

        workerPool.stop();

        assertTrue(completed, "Worker pool failed to process all queued ticks in time");

        Arrays.sort(processingLatenciesNanos);
        double p50Micros = processingLatenciesNanos[(int) (tickCount * 0.50)] / 1_000.0;
        double p95Micros = processingLatenciesNanos[(int) (tickCount * 0.95)] / 1_000.0;
        double p99Micros = processingLatenciesNanos[(int) (tickCount * 0.99)] / 1_000.0;
        double maxMicros = processingLatenciesNanos[tickCount - 1] / 1_000.0;
        double peakQueueUtilization = ((double) maxQueueDepth.get() / queueCapacity) * 100.0;

        System.out.println("\n=======================================================");
        System.out.println("  CONCURRENT WORKER QUEUE BENCHMARK (233 NSE SYMBOLS)  ");
        System.out.println("=======================================================");
        System.out.printf("Worker Count:                             %d workers\n", workerPoolSize);
        System.out.printf("Queue Capacity:                           %,d slots\n", queueCapacity);
        System.out.printf("Peak Queue Depth:                         %,d slots (%.1f%% utilization)\n", maxQueueDepth.get(), peakQueueUtilization);
        System.out.printf("Backpressure Incidents:                   %d\n", backpressureHits);
        System.out.printf("Generated Ticks Rate:                     %,.1f ticks/second\n", genRate);
        System.out.printf("Processed Ticks Rate:                     %,.1f ticks/second\n", procRate);
        System.out.println("-------------------------------------------------------");
        System.out.printf("End-to-End Processing Latency (p50):      %.2f µs\n", p50Micros);
        System.out.printf("End-to-End Processing Latency (p95):      %.2f µs\n", p95Micros);
        System.out.printf("End-to-End Processing Latency (p99):      %.2f µs\n", p99Micros);
        System.out.printf("End-to-End Processing Latency (max):      %.2f µs\n", maxMicros);
        System.out.println("=======================================================\n");

        assertTrue(procRate > 1_000, "Processed throughput should exceed 1,000 ticks/sec");
        assertTrue(p99Micros < 50_000.0, "p99 latency should be under 50ms");
    }
}
