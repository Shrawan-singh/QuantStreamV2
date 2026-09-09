# QuantStream Architecture Documentation

## 1. System Overview

QuantStream is a high-throughput, low-latency market data streaming and quantitative signal processing engine. It transforms continuous real-time market tick streams into actionable, explainable quantitative insights displayed on a live interactive dashboard.

```
+-------------------------------------------------------------------------------+
|                             QuantStream Platform                              |
+-------------------------------------------------------------------------------+

 [Simulation / Live Provider]
              |
              v (Java Producer)
   [MockMarketStreamer]
              |
              v (Producer Record JSON)
      [Apache Kafka: Topic `market-ticks`]
              |
              v (Consumer Poll Batch)
   [StockTickKafkaConsumer]
              |
              v (Validate & Enqueue)
   [TickQueueService (ArrayBlockingQueue)]
              |
   +----------+----------+ (Thread Hand-off via Bounded Queue)
   |                     |
   v                     v
 [TickWorker-0]        [TickWorker-1] ... [TickWorker-N] (ExecutorService Pool)
   |                     |
   +----------+----------+
              |
              v (TickValidationService -> TickProcessingService)
     [AnalyticsEngine]
              |
   +----------+----------+----------+
   |                     |          |
   v                     v          v
[MarketStateStore]  [Indicators] [ConvictionScoreEngine]
   |                     |          |
   +----------+----------+----------+
              |
              v (AnalyticsSnapshot DTO)
   +----------+--------------------+
   |                               |
   v (STOMP Broadcast)             v (Async Bounded Queue)
[MarketWebSocketService]       [AnalyticsPersistenceService]
   |                               |
   v                               v
[React/Next.js UI]             [PostgreSQL 16 Database]
```

---

## 2. Core Architecture Components

### A. Market Data Ingestion Layer
- **`MarketDataProvider`**: Clean abstraction interface supporting `connect()`, `subscribe()`, `unsubscribe()`, `disconnect()`, and `healthStatus()`.
- **`DeterministicTickGenerator`**: Generates realistic pseudorandom geometric Brownian motion price updates with volatility and mean-reverting drifts.
- **`MockMarketStreamer`**: Bridge that runs periodic scheduled simulation ticks and publishes them to the Kafka topic.

### B. Event Streaming Backbone (Apache Kafka)
- Decouples market data producers from backend consumers.
- Topic: `market-ticks` (partitioned, configured for low-latency delivery).
- Serializer/Deserializer: Strongly typed JSON with validation headers.

### C. In-Process Concurrency & Bounded Buffering
- **`TickQueueService`**: Encapsulates a thread-safe, bounded `ArrayBlockingQueue<StockTick>`.
- **Backpressure & Overload Policy**: Drop-oldest or rejection policy preventing `OutOfMemoryError` during unexpected market spikes.
- **`TickWorkerPool`**: Manages dedicated daemon worker threads consuming from the queue using `poll(timeout)`. Graceful shutdown lifecycle management via Spring `SmartLifecycle`.

### D. In-Memory Market State Layer
- **`MarketState`**: Per-symbol thread-safe state container. Maintains bounded circular windows of recent prices (e.g. 100 periods) and volumes, plus session statistics (open, high, low, cumulative volume, tick count) and rolling sums for $O(1)$ SMA calculation.
- **`MarketStateStore`**: `ConcurrentHashMap<String, MarketState>` isolating symbol-level locks so ticks for `RELIANCE` never block ticks for `TCS`.

### E. Quantitative Analytics & Indicator Engine
- **`QuantitativeIndicator`**: Standardized interface for indicator computation.
  1. **SMA**: Simple Moving Average over configurable period $N$ with band-based trend detection.
  2. **EMA**: Exponential Moving Average using recursive multiplier $k = \frac{2}{N+1}$.
  3. **RSI**: Relative Strength Index based on Wilder's smoothed average gains and losses.
  4. **Momentum**: Percentage rate of price change over reference lookback $N$.
  5. **Relative Volume (RVOL)**: Ratio of current tick volume to rolling baseline average volume.

### F. Explainable Conviction Score Engine
- Evaluates composite score from 0.0 to 100.0 based on weighted indicator signals.
- Transparent formula with categorical tiers:
  - `0 - 20`: Very Weak (Bearish)
  - `21 - 40`: Weak
  - `41 - 60`: Neutral
  - `61 - 80`: Strong
  - `81 - 100`: Very Strong (Bullish)
- Transparent breakdown: Trend (+25), Momentum (+25), RSI (+25), Volume (+25) with detailed natural language rationale.

### G. Real-Time WebSocket & REST Delivery
- **Spring WebSocket**: STOMP over SockJS endpoint `/ws` broadcasting snapshots to `/topic/market/all` and `/topic/market/{symbol}`.
- **REST Endpoints**: `/api/stocks`, `/api/analytics`, `/api/watchlist`, `/api/alerts`, `/api/health`, `/api/config`.
- **Async Persistence**: `AnalyticsPersistenceService` persists snapshots off the hot path using an asynchronous bounded worker (`analytics-persistence-worker`) with a 1.5s per-symbol throttle, completely shielding in-memory analytics from disk write latencies.

---

## 3. Local Synthetic Analytics Engine Benchmark (Empirical Results)

The following metrics were empirically measured using `EnginePerformanceBenchmarkTest` executing 10,000 synthetic ticks under realistic 5-symbol rotation on the QuantStream analytics engine:

| Benchmark Metric | Measured Result | Performance Evaluation |
|---|---|---|
| **Hot-Path Pipeline Throughput** | **33,291.8 ticks/sec** | High-throughput in-memory evaluation |
| **State Store Update Latency (p50)** | **2.90 µs** | Optimized circular array buffer with $O(1)$ rolling sums |
| **5x Indicators Computation Latency (p50)** | **6.10 µs** | Analytical evaluation of SMA, EMA, RSI, Momentum, RVOL |
| **Conviction Score Calculation Latency (p50)** | **10.90 µs** | Deterministic weighted arithmetic + factor explanation generation |
| **Engine Processing Latency (p50 / Median)** | **22.00 µs** | **0.022 milliseconds** per tick |
| **Engine Processing Latency (p95)** | **40.60 µs** | **0.041 milliseconds** per tick |
| **Engine Processing Latency (p99)** | **74.00 µs** | **0.074 milliseconds** per tick |
| **Engine Processing Latency (Max)** | **1,620.10 µs** | Initial cold-start JIT compilation spike |
| **Backend Test Suite Coverage** | **45 / 45 Passing (100%)** | Zero failures, zero errors |

> [!NOTE]
> **Measurement Scope**: These measurements represent **local synthetic analytics-engine latency** (JVM in-memory state mutation, rolling indicator mathematics, and conviction score synthesis) executed on local hardware. They reflect internal algorithmic efficiency and should not be confused with external exchange-to-browser network latency or retail broker gateway latency.
