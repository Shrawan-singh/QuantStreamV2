# QuantStream Data Flow & Concurrency Specification

## 1. End-to-End Tick Lifecycle

```
[External Provider / Generator]
   │
   │ 1. Emits StockTick record
   ▼
[MockMarketStreamer / Ingestion Service]
   │
   │ 2. Converts to ProducerRecord<String, StockTick>
   ▼
[StockTickKafkaProducer]
   │
   │ 3. Kafka Network Protocol (Asynchronous Send)
   ▼
[Kafka Broker: Partitioned topic `market-ticks`]
   │
   │ 4. Polled in batches by KafkaListener
   ▼
[StockTickKafkaConsumer]
   │
   │ 5. Validates non-null, positive price
   ▼
[TickQueueService (ArrayBlockingQueue)]
   │
   │ 6. Bounded in-process buffer (Backpressure boundary)
   ▼
[TickWorkerPool: Worker Threads (tick-worker-X)]
   │
   │ 7. Worker thread dequeues tick via poll(timeout)
   ▼
[TickProcessingService.process(tick)]
   │
   │ 8. Invokes AnalyticsEngine
   ▼
[MarketStateStore.update(tick)]
   │
   │ 9. Symbol-level synchronized lock updates state & rolling sums
   ▼
[Indicators & ConvictionScoreEngine]
   │
   │ 10. Computes SMA, EMA, RSI, Momentum, RVOL & ConvictionScore
   ▼
[AnalyticsSnapshot DTO]
   ├───► 11a. MarketWebSocketService (STOMP broadcast to browsers)
   │
   └───► 11b. AnalyticsPersistenceService (Async queue -> PostgreSQL)
```

---

## 2. Concurrency Boundaries and Thread Model

| Stage | Thread Context | Synchronization Mechanism | Overload / Failure Behavior |
|---|---|---|---|
| Ingestion & Streamer | Scheduled Executor / Web Client | Scheduled rate limiting | Drops tick if previous generation incomplete |
| Kafka Producer | Calling thread + Kafka IO Sender | Non-blocking callback | Logged warning; buffer pool bounded |
| Kafka Consumer | Spring Kafka Listener Container | Single or Multi-partition listener | Kafka offset commit retry / DLQ fallback |
| In-Memory Queue | Kafka consumer (producer) & Workers (consumer) | `ArrayBlockingQueue<StockTick>` (bounded) | `offer()` timeout; drop/backpressure logged |
| Worker Pool | Fixed thread pool (`tick-worker-X`) | Dedicated thread per worker; stateless loop | Catches `InterruptedException`, cleans up on shutdown |
| Market State Store | Worker threads | `ConcurrentHashMap` + Symbol `synchronized` | Lock confined to single symbol; 0 cross-symbol contention |
| WebSocket Broadcast | Worker threads | Spring `SimpMessagingTemplate` | Exception caught; client drop does not halt worker |
| Database Persistence | Dedicated persistence worker (`analytics-persistence-worker`) | Bounded `LinkedBlockingQueue` + throttling | 1.5s per-symbol throttle; drops excess during bursts |

---

## 3. Why Bounded Buffers & Isolation Matter

1. **Memory Protection**: An unbounded queue under sustained market spikes would lead to heap exhaustion and JVM crash (`OutOfMemoryError`). QuantStream enforces strict upper limits on both `ArrayBlockingQueue` (default 1,000) and `MarketState` historical windows (default 100).
2. **Decoupled Database I/O**: PostgreSQL disk writes take 1–10ms, which is 100x slower than in-memory tick calculation (sub-millisecond). By utilizing `AnalyticsPersistenceService`, slow disk I/O cannot cause queue backup in the market data processing pipeline.
