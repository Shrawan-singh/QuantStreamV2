# QuantStream Kafka Streaming & Messaging Architecture

## 1. Overview & Architectural Role

In QuantStream, Apache Kafka serves as the **durable, decoupled, distributed event ingress backbone**. It separates external market data providers (simulation feeds, exchange gateways, or third-party market data vendors) from the internal quantitative analytics engine.

```
[External Data Source / Mock Streamer]
                 │
                 ▼
       [Kafka Producer (Key: Symbol)]
                 │
                 ▼
  [Apache Kafka Topic: `market-ticks`]
       ├── Partition 0 (RELIANCE, INFY)
       ├── Partition 1 (TCS, HDFCBANK)
       └── Partition 2 (ICICIBANK, ...)
                 │
                 ▼
     [Kafka Consumer Group]
                 │
                 ▼
     [Bounded ArrayBlockingQueue]
                 │
                 ▼
       [Tick Worker Pool]
```

---

## 2. Kafka vs. In-Process ArrayBlockingQueue: Architectural Rationale

A common question in distributed streaming architectures is: **"Why do we need both Apache Kafka AND an in-process `ArrayBlockingQueue`?"**

QuantStream intentionally pairs these two technologies because they operate at fundamentally different tiers and solve distinct concurrency problems:

| Dimension | Apache Kafka | In-Process `ArrayBlockingQueue` |
|---|---|---|
| **Tier** | External Distributed Messaging Middleware | Internal In-Process JVM Concurrency Buffer |
| **Purpose** | Ingress decoupling, horizontal scalability, replayability, persistence | Microsecond thread hand-off between IO consumer and compute worker pool |
| **Latency** | Network IO + disk sync: **1–5 ms** | In-memory pointer swap: **sub-microsecond (< 1 µs)** |
| **Throughput** | 50,000+ msgs/sec over network socket | 30,000–100,000+ ticks/sec hot-path memory handoff |
| **Persistence** | Durable disk commit with configurable retention | Volatile heap memory (`StockTick` object references) |
| **Failure Domain** | Independent cluster process; survives JVM restarts | Scoped strictly to backend JVM lifecycle |
| **Backpressure** | Partition lag, fetch pauses, flow control | Bounded capacity (1,000), drop/offer timeout policy |
| **Data Format** | Serialized JSON bytes over TCP wire | Zero-serialization native Java object references |

### Key Takeaway
- **Kafka** guarantees that if the backend JVM undergoes a graceful restart or temporary freeze, external market ticks are not lost; they buffer safely on the broker.
- **`ArrayBlockingQueue`** ensures that within the JVM, the Kafka consumer thread does not do heavy mathematical work (indicators, state updates, conviction scores). Instead, it rapidly hands off ticks to a pool of worker threads with zero serialization overhead.

---

## 3. Topic Architecture & Partitioning Strategy

### Topic: `market-ticks`
- **Default Partitions**: 3 (scalable based on core count and consumer instances)
- **Replication Factor**: 1 (local development / single broker); 3 in production clusters.
- **Retention Policy**: `delete`, retention period 24 hours (configurable for daily market session replay).
- **Cleanup Policy**: Time-based log truncation.

### Key Partitioning Guarantee
Every `ProducerRecord` published to Kafka sets the **Stock Symbol** (e.g. `"RELIANCE"`) as the message key:
```java
ProducerRecord<String, StockTick> record = 
    new ProducerRecord<>("market-ticks", tick.getSymbol(), tick);
```
- **Why Symbol as Key?** Kafka uses murmur2 hashing on the key to assign partitions. By keying on symbol, **all ticks for a given symbol are guaranteed to arrive in strict chronological order at the same partition**, preventing out-of-order tick processing for individual instruments.

---

## 4. Producer & Consumer Configuration

### Producer Configuration (`StockTickKafkaProducer`)
- **Bootstrap Servers**: `localhost:9092` (host) or `quantstream-kafka:29092` (container)
- **Key Serializer**: `org.apache.kafka.common.serialization.StringSerializer`
- **Value Serializer**: `org.springframework.kafka.support.serializer.JsonSerializer`
- **Idempotence**: Enabled (`enable.idempotence=true`)
- **Acks**: `acks=1` for low-latency market streaming
- **Linger & Batching**: `linger.ms=1`, `batch.size=16384` (sub-millisecond batch flush for high tick rates)

### Consumer Configuration (`StockTickKafkaConsumer`)
- **Consumer Group ID**: `quantstream-analytics-group`
- **Key Deserializer**: `org.apache.kafka.common.serialization.StringDeserializer`
- **Value Deserializer**: `org.springframework.kafka.support.serializer.ErrorHandlingDeserializer` wrapping `JsonDeserializer<StockTick>`
- **Auto-Offset Reset**: `latest` (for live market analytics) or `earliest` (for historical session replay)
- **Batch Processing**: Spring Kafka `@KafkaListener` receives batches or individual records, validates structural correctness via `TickValidationService`, and performs non-blocking `offer()` into `TickQueueService`.

---

## 5. Docker Networking & Dual Listeners

In modern containerized setups, the Kafka broker runs inside Docker, while client applications (like our Spring Boot backend during development) often run directly on the host machine. To satisfy both environments seamlessly, QuantStream configures Kafka with **dual listeners**:

```yaml
# docker-compose.yml snippet
KAFKA_LISTENERS: PLAINTEXT://0.0.0.0:9092,PLAINTEXT_INTERNAL://0.0.0.0:29092
KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://localhost:9092,PLAINTEXT_INTERNAL://quantstream-kafka:29092
KAFKA_LISTENER_SECURITY_PROTOCOL_MAP: PLAINTEXT:PLAINTEXT,PLAINTEXT_INTERNAL:PLAINTEXT
```

1. **`PLAINTEXT://localhost:9092`**: Advertised to host processes (e.g. IDE, local Maven runs, testing CLI).
2. **`PLAINTEXT_INTERNAL://quantstream-kafka:29092`**: Advertised to other containers inside the Docker bridge network.

---

## 6. Failure Modes & Resilience Handling

1. **Broker Unreachable on Startup**:
   - The backend uses non-blocking consumer lifecycle management. If Kafka is temporarily down, the consumer backoff reconnects periodically with exponential backoff (`BackOff` policy).
2. **Deserialization Poison Pills**:
   - The consumer uses Spring Kafka's `ErrorHandlingDeserializer`. Malformed JSON or schema mismatches do not crash the listener loop; error records are logged and routed away without stalling partition consumption.
3. **Queue Saturation & Backpressure**:
   - If the in-process `ArrayBlockingQueue` is full (e.g., workers temporarily saturated), the consumer's `offer(tick, 5, TimeUnit.MILLISECONDS)` blocks briefly. If queue capacity remains exhausted, backpressure metrics increment and oldest/surplus ticks are handled gracefully, protecting heap memory.
