# QuantStream Concurrency & Threading Architecture

## 1. Concurrency Goals & Principles

In QuantStream, concurrency serves specific architectural goals:
1. **Decouple Ingestion from Analytics**: Prevent network or I/O delays during market data arrival from stalling compute-intensive quantitative indicators.
2. **Prevent Head-of-Line Blocking**: Ensure ticks for distinct equity symbols (e.g. `RELIANCE` vs. `TCS`) process concurrently without contending for the same synchronization locks.
3. **Isolate Hot Processing from Persistence**: Ensure slow database disk I/O in PostgreSQL or network latency to WebSocket clients never stalls the real-time worker pool.
4. **Enforce Bounded Memory**: Guarantee that high market event burst rates are buffered within strictly bounded queues with explicit backpressure, preventing `OutOfMemoryError`.

---

## 2. In-Process Concurrency Pipeline

```
[Kafka Consumer / Ingestion Thread]
               │
               ▼
   [TickQueueService: ArrayBlockingQueue<StockTick>] (Bounded capacity: 1,000)
               │
   ┌───────────┼───────────┬───────────┐
   ▼           ▼           ▼           ▼
[Worker 0]  [Worker 1]  [Worker 2]  [Worker 3] (ExecutorService ThreadPool)
   │           │           │           │
   └───────────┼───────────┴───────────┘
               ▼
     [TickProcessingService]
               │
   ┌───────────┴───────────┐
   ▼                       ▼
[MarketStateStore]    [AnalyticsEngine]
(ConcurrentHashMap)   (Stateless, Pure Functions)
   │                       │
   ▼                       ▼
[Per-Symbol Lock]     [ConvictionScoreEngine]
               │
   ┌───────────┴───────────┐
   ▼ (Non-blocking STOMP)  ▼ (Async Single-Thread Queue)
[MarketWebSocketService] [AnalyticsPersistenceService]
                           │
                           ▼
                      [PostgreSQL 16]
```

---

## 3. Core Java Concurrency Utilities

### A. Bounded `ArrayBlockingQueue<StockTick>`
- **Location**: `com.quantstream.backend.processing.TickQueueService`
- **Capacity**: Configurable (`quantstream.streaming.queue-size`, default: `1,000`).
- **Producer Behavior**:
  ```java
  while (accepting.get()) {
      if (queue.offer(tick, 1, TimeUnit.SECONDS)) {
          return;
      }
      logger.warn("Tick queue full. Applying backpressure. queueSize={}/{}", queue.size(), capacity);
  }
  ```
  Applies explicit backpressure to the Kafka consumer when workers cannot keep up with incoming volume bursts.
- **Consumer Behavior**:
  `queue.poll(500, TimeUnit.MILLISECONDS)` allows graceful shutdown detection when `accepting` is set to `false`.

### B. Worker Pool (`ExecutorService`)
- **Location**: `com.quantstream.backend.processing.TickWorkerPool`
- **Pool Size**: Configurable (`quantstream.streaming.worker-pool-size`, default: 4 workers).
- **Thread Factory**: Custom `WorkerThreadFactory` assigning deterministic thread names: `tick-worker-0`, `tick-worker-1`, etc.
- **Lifecycle**: Implements Spring's `SmartLifecycle` and `@PreDestroy`. During shutdown:
  1. Sets `running.set(false)`.
  2. Closes `TickQueueService.shutdown()` so no further ticks are queued.
  3. Invokes `executorService.shutdownNow()` and awaits termination up to `shutdownTimeoutMs` (5,000ms).

### C. In-Memory Thread Safety & Symbol-Level Isolation
- **Location**: `com.quantstream.backend.analytics.state.MarketStateStore` & `MarketState`
- **Structure**:
  ```java
  private final ConcurrentHashMap<String, MarketState> states = new ConcurrentHashMap<>();
  ```
- **Locking Granularity**:
  `MarketStateStore.update(tick)` retrieves the symbol's dedicated `MarketState` instance using `computeIfAbsent()`.
  Updates to `MarketState` synchronize **only** on that specific symbol's instance monitor:
  ```java
  public synchronized void update(StockTick tick) { ... }
  public synchronized Snapshot getSnapshot() { ... }
  ```
  **Result**: Concurrency between symbols is 100% parallel. Ticks for `RELIANCE` and `TCS` execute simultaneously on different worker threads with zero lock contention.
- **Stateless Indicators**:
  Indicator components (`SmaIndicator`, `EmaIndicator`, `RsiIndicator`, `MomentumIndicator`, `RelativeVolumeIndicator`, `ConvictionScoreEngine`) maintain **zero mutable state**. They operate as pure functions over immutable `MarketState.Snapshot` records, allowing concurrent execution across all worker threads without synchronization overhead.

### D. Asynchronous Persistence Decoupling
- **Location**: `com.quantstream.backend.service.AnalyticsPersistenceService`
- **Worker**: Dedicated single daemon thread `analytics-persistence-worker`.
- **Buffer**: `LinkedBlockingQueue<AnalyticsSnapshot>(2000)`.
- **Throttling**: At most one snapshot persisted to PostgreSQL per symbol every 1,500ms (`PERSIST_THROTTLE_MS`).
- **Non-blocking Enqueue**: Uses `queue.offer(snapshot)`; drops excess snapshot writes during bursts without impacting live real-time WebSocket delivery.

---

## 4. Graceful Shutdown Sequence

When the JVM or Spring context initiates shutdown:
1. **Market Data Feeds**: `MockMarketDataProvider` / `LiveMarketDataProvider` disconnect and stop scheduled emission.
2. **Kafka Consumer**: Spring Kafka listener container stops polling `market-ticks`.
3. **Internal Queue**: `TickQueueService.shutdown()` closes ingress to new submissions.
4. **Worker Pool**: `TickWorkerPool` drains in-flight ticks and terminates workers.
5. **Persistence Worker**: `AnalyticsPersistenceService.shutdown()` drains pending database commits and shuts down executor.
6. **Kafka Producer & Database**: Spring closes KafkaTemplate and HikariCP connection pools cleanly.
