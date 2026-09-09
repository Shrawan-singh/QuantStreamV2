# MASTER PROJECT BUILD PROMPT — QUANTSTREAM

You are a senior Java architect, backend engineer, distributed-systems engineer, quantitative software engineer, database designer, DevOps engineer, and React/Next.js frontend engineer.

Build a complete, working, production-style academic full-stack project called:

# QuantStream

## Real-Time Market Data Streaming & Quantitative Signal Processing Engine

This is a Second-Year Computer Engineering Full Stack Java Programming (FSJP) project.

The project must demonstrate strong understanding and practical implementation of:

* Core Java
* Object-Oriented Programming
* Java Collections
* Java Concurrency
* Multithreading
* ExecutorService
* Producer-Consumer architecture
* BlockingQueue / ArrayBlockingQueue
* WebSocket networking
* Spring Boot
* REST APIs
* Apache Kafka
* Kafka Producer/Consumer
* In-memory data processing
* Quantitative financial indicators
* Explainable scoring
* PostgreSQL
* React / Next.js
* Real-time frontend updates
* Error handling
* Logging
* Testing
* Dockerized local development

The final result must be a coherent end-to-end application, not a collection of disconnected demos.

---

# 1. PRODUCT DEFINITION

QuantStream is a real-time market-data analytics platform.

Its purpose is to continuously receive live market events, process them through a streaming pipeline, calculate quantitative indicators on recent market data, combine those indicators into an explainable Conviction Score, and display the results through a real-time web dashboard.

The product is NOT:

* a stock brokerage
* an order-execution system
* a replacement for Zerodha, Upstox, Groww, or TradingView
* a guaranteed stock-prediction system
* a financial-advice engine
* an ML model unless an ML module is explicitly implemented later

The product IS:

* a real-time market-monitoring system
* a quantitative analytics engine
* a streaming-data processing demonstration
* a decision-support analytics interface
* a Java concurrency and distributed-systems project

The core product promise is:

"Take continuously arriving market events and transform them into understandable, real-time quantitative information."

---

# 2. CORE USER EXPERIENCE

A user opens the QuantStream dashboard.

The dashboard shows a watchlist containing multiple stocks.

For each stock, the user can see:

* Symbol
* Company name
* Current price
* Price change
* Percentage change
* Trading volume
* Relative volume
* Moving average
* RSI
* Momentum
* Conviction Score
* Signal state
* Last update time
* Connection/data status

The dashboard must update automatically as new market events arrive.

The user should not have to manually refresh the page.

The user can click a stock to see a detailed analysis page.

The detailed page should show:

* Current price
* Price change
* Chart
* Technical indicators
* Quantitative score
* Score breakdown
* Signal explanation
* Volume information
* Recent updates
* Data timestamp
* Whether the data is live or demo/simulated

The application should also provide a quantitative scanner/ranking view where stocks can be ranked according to their current Conviction Score.

The user should be able to understand not only:

"Score = 82"

but also:

"Why is the score 82?"

---

# 3. VERY IMPORTANT PRODUCT PRINCIPLE

Do not make unsupported claims such as:

* "Zerodha is slow because it uses REST"
* "Zerodha takes 2–5 seconds"
* "Groww cannot use WebSockets"
* "Other brokers do not use in-memory processing"
* "Our system is faster than Zerodha"
* "Our system guarantees sub-50ms latency"
* "Kafka automatically makes the system faster"

Do not compare QuantStream to production brokerages using invented internal architecture.

Existing financial platforms already use real-time market feeds, WebSockets, charts, technical indicators and analytics.

QuantStream's differentiation is the focused integration of:

LIVE MARKET EVENTS
→ EVENT STREAMING
→ CONCURRENT JAVA PROCESSING
→ IN-MEMORY QUANTITATIVE ANALYSIS
→ EXPLAINABLE SCORING
→ REAL-TIME WEB DELIVERY

The project should demonstrate this architecture clearly.

---

# 4. HIGH-LEVEL FINAL ARCHITECTURE

Implement the following logical architecture:

FINANCIAL MARKET DATA PROVIDER
↓
MARKET DATA WEBSOCKET CLIENT
↓
JAVA MARKET DATA INGESTION SERVICE
↓
KAFKA PRODUCER
↓
APACHE KAFKA
↓
KAFKA CONSUMER
↓
JAVA / SPRING BOOT PROCESSING PIPELINE
↓
IN-PROCESS CONCURRENCY / BLOCKINGQUEUE
↓
IN-MEMORY MARKET STATE
↓
QUANTITATIVE INDICATOR ENGINE
↓
CONVICTION SCORE ENGINE
↓
RESULT EVENT
↓
SPRING WEBSOCKET SERVER
↓
REACT / NEXT.JS FRONTEND

Separately:

SPRING BOOT
↕
POSTGRESQL

PostgreSQL must be treated as persistent storage, not as the mandatory storage mechanism for every hot-path calculation.

---

# 5. MARKET DATA PROVIDER

Integrate a real financial market-data provider that provides legal developer access to market data and, where available, streaming/WebSocket data.

Do NOT hard-code assumptions about a provider's pricing or free limits.

Make the market-data provider configurable using environment variables.

Example configuration:

MARKET_DATA_PROVIDER
MARKET_DATA_API_KEY
MARKET_DATA_WEBSOCKET_URL
MARKET_DATA_REST_URL

Design the provider integration behind an interface so the provider can be replaced.

Example:

MarketDataProvider
├── connect()
├── subscribe(symbol)
├── unsubscribe(symbol)
├── disconnect()
└── healthStatus()

Create an abstraction rather than embedding provider-specific logic everywhere.

---

# 6. MARKET TICK MODEL

Create a strongly typed Java domain model for each market event.

Example fields:

StockTick:

* id
* symbol
* price
* volume
* timestamp
* eventType
* source
* exchange/instrument information if available

Use appropriate Java types.

Do not use String for every field.

Use:

* BigDecimal where appropriate for financial prices
* long / Instant for timestamps where appropriate
* enums for event types where appropriate

Do not blindly use double for all monetary values.

---

# 7. DATA INGESTION SERVICE

Create a dedicated Java service responsible for receiving market events.

Responsibilities:

1. Establish the external WebSocket connection.
2. Authenticate if required.
3. Subscribe to selected instruments.
4. Receive incoming messages.
5. Parse incoming data.
6. Validate required fields.
7. Convert raw messages into StockTick objects.
8. Handle malformed messages.
9. Handle disconnects.
10. Attempt controlled reconnection.
11. Publish valid events to the next stage.
12. Record metrics and logs.

Do not perform expensive indicator calculations directly inside the WebSocket callback.

The WebSocket callback should remain lightweight.

This is important because data ingestion and analytics should be separated.

---

# 8. PRODUCER-CONSUMER DESIGN

Use a producer-consumer architecture.

The market-data ingestion layer acts as the producer.

The processing layer acts as the consumer.

Use Java concurrency utilities.

At an appropriate internal boundary, use:

ArrayBlockingQueue<StockTick>

The producer:

* receives a tick
* validates it
* places it into the queue

The consumer:

* waits for available ticks
* retrieves them
* processes them
* updates market state
* triggers analytics

The queue must be bounded.

Do not use an unlimited queue for everything.

Implement sensible behavior for:

* queue full
* queue empty
* shutdown
* interruption
* overload

Document the chosen overload strategy.

---

# 9. EXECUTORS AND THREADING

Use ExecutorService and/or an appropriate ThreadPoolExecutor where suitable.

Do not create unmanaged threads unnecessarily.

Design clear responsibilities.

A possible structure is:

INGESTION THREAD / CALLBACK
↓
QUEUE
↓
PROCESSING WORKERS
↓
ANALYTICS
↓
RESULT DISPATCHER

The exact number of threads must be configurable.

Do not create one thread per stock.

Avoid excessive thread creation.

Use a controlled worker-pool architecture.

Every executor must have a proper shutdown strategy.

Handle InterruptedException correctly.

Do not swallow interruptions.

---

# 10. WHY CONCURRENCY EXISTS

The architecture must make these responsibilities independent:

1. Receiving data
2. Buffering data
3. Processing data
4. Calculating indicators
5. Sending results

A slow operation in one stage should not unnecessarily block unrelated operations.

Use concurrency to improve throughput and responsiveness, not simply because "multithreading is cool."

---

# 11. APACHE KAFKA

Use Apache Kafka as the event-streaming backbone.

Kafka should sit between market-data ingestion and downstream processing.

Architecture:

Market Data
↓
Kafka Producer
↓
Kafka Topic
↓
Kafka Consumer
↓
Spring Boot Processing

Create a topic such as:

market-ticks

Use configuration rather than hard-coding infrastructure details.

Kafka is used for:

* event streaming
* decoupling producers and consumers
* buffering bursts
* durable event retention according to configuration
* scalability
* replay/recovery possibilities
* multiple consumers if needed

Do NOT describe Kafka as an ML model.

Do NOT use Kafka as though it directly calculates RSI.

Do NOT claim Kafka automatically lowers latency.

Explain Kafka correctly as infrastructure.

---

# 12. KAFKA EVENT SCHEMA

Define a clear event schema.

A market event should include enough information for downstream processing.

Example conceptual structure:

{
"eventId": "...",
"symbol": "RELIANCE",
"price": 2907.20,
"volume": 1250000,
"timestamp": "...",
"source": "..."
}

Use reliable serialization.

JSON may be used for academic simplicity unless another serialization technology is intentionally chosen.

Validate messages at the consumer boundary.

Handle malformed events gracefully.

---

# 13. KAFKA CONSUMER

Create a Spring Boot Kafka consumer.

Responsibilities:

* subscribe to market-ticks
* deserialize events
* validate events
* hand them to the processing layer
* handle processing failures
* log failures
* support retry/error-handling strategy
* expose consumer health where appropriate

Do not put all business logic into the Kafka listener method.

The listener should delegate to proper services.

---

# 14. INTERNAL BLOCKING QUEUE VS KAFKA

Clearly distinguish these.

Kafka:

* distributed
* external to the Java process
* event-streaming infrastructure
* decouples services/processes

ArrayBlockingQueue:

* inside the Java application
* thread-safe
* bounded
* used for in-process producer-consumer coordination

It is valid for the project to use both because they solve different problems.

---

# 15. IN-MEMORY MARKET STATE

Create a dedicated in-memory state-management layer.

It should maintain only the data needed for fast calculations.

Possible structure:

Map<String, MarketState>

For each symbol maintain:

* latest price
* recent prices
* recent volumes
* previous price
* rolling sums
* rolling averages
* indicator state
* timestamps
* latest Conviction Score

Use bounded windows.

Do NOT allow unbounded historical market data to remain in memory.

For example:

recentPrices = latest N values

recentVolumes = latest N values

Make lookback periods configurable.

---

# 16. THREAD SAFETY OF IN-MEMORY STATE

The state can be accessed by multiple threads.

Therefore, design concurrency carefully.

Choose appropriate mechanisms such as:

* ConcurrentHashMap
* synchronized sections where justified
* locks where justified
* immutable result objects
* atomic values where appropriate

Avoid unnecessary global synchronization.

Do not put one giant synchronized lock around the entire engine if avoidable.

Document why the selected concurrent structures are safe.

---

# 17. QUANTITATIVE ENGINE

Build a dedicated quantitative analytics module.

It should receive the current market state and calculate selected technical indicators.

At minimum support:

1. Moving Average
2. RSI
3. Momentum
4. Relative Volume / Volume anomaly

Design each indicator as a separate component.

For example:

Indicator
├── MovingAverageIndicator
├── RsiIndicator
├── MomentumIndicator
└── RelativeVolumeIndicator

Use interfaces where appropriate.

Example:

interface Indicator<T> {
T calculate(...);
}

The exact interface may be adapted to the domain.

Keep indicators independently testable.

---

# 18. MOVING AVERAGE

Implement Simple Moving Average.

SMA:

SMA = sum of prices in window / number of prices

Example:

100, 102, 104, 106, 108

SMA = 104

Prefer an efficient rolling implementation for streaming data rather than unnecessarily recalculating the entire window each time.

Maintain a rolling sum where appropriate.

Make the lookback configurable.

Example:

MA_PERIOD=20

The system should allow changing the period through configuration.

---

# 19. RSI

Implement Relative Strength Index.

Use a clearly defined lookback period.

The implementation must document the selected RSI methodology.

Do not casually mix incompatible formulations.

The result should be approximately on a 0–100 scale.

Interpretation should be configurable/documented.

Do not state:

RSI > 70 = guaranteed sell

or:

RSI < 30 = guaranteed buy

Instead treat RSI as one signal among multiple signals.

---

# 20. MOMENTUM

Implement a clearly defined momentum calculation.

For example, percentage change over a configurable lookback:

Momentum =
((CurrentPrice - PreviousReferencePrice) / PreviousReferencePrice) × 100

Clearly document:

* lookback period
* formula
* interpretation

Do not claim momentum predicts future returns.

---

# 21. RELATIVE VOLUME

Implement a relative-volume metric.

Example concept:

Relative Volume =
Current Volume / Average Volume

Make the averaging period configurable.

Example:

Current Volume = 2,000,000
Average Volume = 1,000,000

Relative Volume = 2.0x

Interpret this as unusually high activity relative to the baseline, not automatically bullish.

---

# 22. INDICATOR OUTPUT MODEL

Create a structured result.

Example:

IndicatorSnapshot:

* symbol
* timestamp
* movingAverage
* rsi
* momentum
* relativeVolume

The result should also contain signal classifications where appropriate.

For example:

momentumSignal = POSITIVE

Do not mix UI strings directly into core calculation logic.

---

# 23. CONVICTION SCORE ENGINE

This is the main application-level feature.

Build a transparent scoring engine.

The score must be derived from defined rules and indicators.

Normalize indicator contributions to a common range.

Produce a score from:

0 to 100

The scoring method must be deterministic for identical inputs.

Do not use random values.

Do not use an ML model unless explicitly implemented and trained.

The system should produce both:

* total score
* contribution breakdown

Example:

Score = 82

Breakdown:

Trend = +22
Momentum = +21
Volume = +19
RSI = +20

These numbers are examples only.

Define actual weights in configuration/constants.

For example:

TREND_WEIGHT
MOMENTUM_WEIGHT
VOLUME_WEIGHT
RSI_WEIGHT

Do not hard-code unexplained numbers everywhere.

---

# 24. SCORING DESIGN

Create a scoring pipeline such as:

Raw Indicator
↓
Normalize
↓
Classify Signal
↓
Calculate Weighted Contribution
↓
Aggregate
↓
Clamp/Normalize to 0–100
↓
Generate Explanation

The engine should produce a structured object:

ConvictionScore:

* symbol
* score
* category
* timestamp
* trendContribution
* momentumContribution
* volumeContribution
* rsiContribution
* explanation
* indicatorSnapshot

Categories could be:

0–20 = Very Weak
21–40 = Weak
41–60 = Neutral
61–80 = Strong
81–100 = Very Strong

These thresholds must be configurable.

Do not label these categories as guarantees of price movement.

---

# 25. EXPLAINABILITY

Every score should have an explanation.

Example:

Score: 82

Explanation:

* Trend: positive because price is above reference moving average
* Momentum: positive due to positive lookback return
* Volume: elevated relative to rolling baseline
* RSI: moderately positive

The UI must allow the user to understand how the score was produced.

Do not simply display "82" with no explanation.

---

# 26. IMPORTANT FINANCIAL SAFETY / PRODUCT LANGUAGE

The system should be described as:

* analytical
* quantitative
* informational
* decision-support

Do not describe the result as:

* guaranteed prediction
* guaranteed profit
* guaranteed buy/sell recommendation

Use language such as:

"Current quantitative conditions"

"Technical signal"

"Analytical score"

"Signal strength"

"Decision-support information"

Add a disclaimer in the application:

"QuantStream provides analytical information based on defined quantitative indicators. It is not financial advice and does not guarantee future market performance."

---

# 27. REAL-TIME RESULT PIPELINE

After indicator and score calculation, produce a structured analytics event.

Example:

{
"symbol": "RELIANCE",
"price": 2907.20,
"rsi": 64.2,
"movingAverage": 2885.1,
"momentum": 1.28,
"relativeVolume": 2.1,
"convictionScore": 82,
"timestamp": "...",
"signals": {
"trend": "POSITIVE",
"momentum": "POSITIVE",
"volume": "ELEVATED",
"rsi": "POSITIVE"
}
}

This event becomes the payload delivered to the frontend.

---

# 28. SPRING BOOT BACKEND

Build the backend as a clean Spring Boot application.

Use a layered structure such as:

controller
service
repository
domain/model
dto
config
messaging
websocket
analytics
exception
util

Do not put everything into one package or one class.

Use dependency injection.

Use constructor injection.

Prefer interfaces where the abstraction has real value.

---

# 29. REST API

Expose REST endpoints for operations that are naturally request-response based.

Possible APIs:

GET /api/stocks
GET /api/stocks/{symbol}
GET /api/stocks/{symbol}/history
GET /api/stocks/{symbol}/analytics
GET /api/watchlist
POST /api/watchlist
DELETE /api/watchlist/{symbol}
GET /api/health
GET /api/config

Adapt endpoints according to actual implementation.

Do not use REST polling as the mechanism for second-by-second live updates.

Use WebSockets for continuous live data.

---

# 30. WEBSOCKET SERVER

Expose a Spring WebSocket endpoint for frontend live updates.

For example:

/ws/market

or an appropriately structured endpoint.

When a new ConvictionScore / analytics result becomes available:

1. Process event.
2. Produce result.
3. Broadcast to relevant connected frontend clients.

Support multiple clients.

Handle disconnected clients gracefully.

Do not block the entire processing pipeline because one browser client is disconnected.

---

# 31. WEBSOCKET MESSAGE DESIGN

Use a clean JSON payload.

Example:

{
"type": "MARKET_UPDATE",
"symbol": "RELIANCE",
"price": 2907.20,
"priceChangePercent": 1.28,
"volume": 1250000,
"rsi": 64.2,
"movingAverage": 2885.1,
"momentum": 1.28,
"relativeVolume": 2.1,
"convictionScore": 82,
"timestamp": "..."
}

Support message types where useful:

* MARKET_UPDATE
* SCORE_UPDATE
* ALERT
* CONNECTION_STATUS
* ERROR

---

# 32. FRONTEND

Build a polished React or Next.js frontend.

Use a modern financial dashboard style.

The frontend should feel like a real analytics product, not a basic college CRUD application.

Use:

* responsive layout
* left navigation/sidebar
* dashboard cards
* watchlist
* charts
* indicator cards
* score gauge
* tables
* status indicators
* responsive design
* loading states
* empty states
* error states

Avoid excessive visual clutter.

---

# 33. MAIN DASHBOARD

Create a dashboard with:

Top:

* market/system status
* WebSocket connected indicator
* last update
* selected market

Main:

* watchlist
* price cards
* Conviction Score
* change %
* volume

Analytics:

* RSI
* Moving Average
* Momentum
* Relative Volume

Ranking:

Top Quantitative Signals

Example:

1. RELIANCE — 82
2. HDFC — 78
3. ICICI — 74
4. TCS — 67

The ranking must come from backend data, not hard-coded mock values in the production path.

---

# 34. STOCK DETAIL PAGE

When the user clicks a stock, show:

* Stock name
* symbol
* current price
* percentage change
* price chart
* volume chart if feasible
* RSI
* moving average
* momentum
* relative volume
* Conviction Score
* score breakdown
* signal explanation
* recent updates

Example:

RELIANCE
₹2,907.20
+1.28%

Conviction Score
82 / 100

Trend          Positive
Momentum       Strong
Volume         Elevated
RSI            Positive

The exact values must come from the backend.

---

# 35. QUANTITATIVE SCANNER / RANKING PAGE

Create a dedicated scanner view.

Purpose:

Allow users to see which stocks currently exhibit the strongest quantitative conditions according to the configured scoring methodology.

Columns:

* Rank
* Symbol
* Price
* Change %
* RSI
* Momentum
* Relative Volume
* Score
* Signal

Allow sorting by:

* Score
* Change %
* Volume
* Momentum

Optionally allow filters.

Do not call this a guaranteed "stock picker."

Call it:

"Quantitative Scanner"

or:

"Signal Ranking"

---

# 36. ALERT SYSTEM

Implement an alert mechanism.

Users can define conditions such as:

* score >= 80
* score <= 30
* relative volume >= 2
* RSI >= threshold
* RSI <= threshold

Alert events should be generated by backend logic.

The frontend should show:

* alert list
* time
* symbol
* condition
* current value

Do not send financial recommendations.

An alert should say:

"RELIANCE crossed Conviction Score 80"

not:

"BUY RELIANCE NOW"

---

# 37. FRONTEND WEBSOCKET MANAGEMENT

Implement a dedicated frontend WebSocket service/hook.

It should:

* connect
* authenticate if required
* subscribe
* receive updates
* parse JSON
* update state
* reconnect after failure
* stop reconnecting when component/app is intentionally unmounted
* show connection status

Use exponential backoff or controlled retry behavior.

Avoid reconnect storms.

---

# 38. STATE MANAGEMENT

Use sensible React state architecture.

Possible states:

* selectedStock
* watchlist
* marketData
* analytics
* alerts
* connectionStatus

Use Context, Zustand, Redux, or another appropriate state solution only if necessary.

Do not overengineer state management for a small application.

---

# 39. POSTGRESQL

Use PostgreSQL for persistent application data.

Potential tables:

users
watchlists
watchlist_items
symbols
alerts
user_preferences
historical_market_data
analytics_snapshots
application_logs if appropriate

Do not force every live tick through PostgreSQL synchronously.

For the hot analytics path, use in-memory state.

Persistent storage should be asynchronous or appropriately separated where practical.

---

# 40. DATABASE DESIGN

Use proper relational modeling.

Example:

## users

id
name
email
password_hash
created_at

## watchlists

id
user_id
name
created_at

## watchlist_items

id
watchlist_id
symbol

## alerts

id
user_id
symbol
condition_type
threshold
enabled
created_at

## analytics_snapshots

id
symbol
timestamp
price
rsi
moving_average
momentum
relative_volume
conviction_score

Use:

* primary keys
* foreign keys
* indexes
* timestamps

Do not store everything in one giant table.

---

# 41. HISTORICAL DATA

Historical data is useful for:

* charts
* analysis
* score review
* future backtesting

Do not confuse historical persistence with the real-time hot path.

A possible flow:

LIVE EVENT
↓
PROCESS
↓
ANALYTICS
↓
WEBSOCKET

Separately:

ANALYTICS RESULT
↓
ASYNC PERSISTENCE
↓
POSTGRESQL

The persistence strategy must not unnecessarily block the immediate UI update.

---

# 42. ERROR HANDLING

Handle at minimum:

Market API unavailable
Kafka unavailable
Kafka consumer failure
Malformed market data
Unknown symbol
Database failure
WebSocket disconnect
Frontend reconnect
Queue overload
Invalid configuration
Missing API credentials
Graceful application shutdown

Create centralized exception handling for REST endpoints.

Return useful HTTP statuses and error messages.

Do not expose sensitive stack traces to users.

---

# 43. LOGGING

Use structured application logging.

Log events such as:

* application startup
* market feed connection
* subscription
* reconnect
* Kafka producer errors
* Kafka consumer errors
* malformed messages
* processing failures
* WebSocket client connection/disconnection
* database errors

Avoid printing everything with System.out.println in the final application.

Use a proper logging framework supported by Spring Boot.

---

# 44. OBSERVABILITY

Expose useful application metrics if practical.

Track:

* ticks received
* ticks processed
* processing errors
* queue size
* Kafka consumer lag if available
* connected WebSocket clients
* analytics calculations
* processing duration
* end-to-end latency measurements

Provide at least basic application health information.

---

# 45. LATENCY MEASUREMENT

DO NOT hard-code claims such as:

"QuantStream = 30ms"

or:

"Existing apps = 2–5 seconds"

Instead instrument timestamps.

Capture:

T1 = market event received

T2 = Kafka publish

T3 = Kafka consumer received

T4 = analytics processing finished

T5 = WebSocket broadcast

T6 = frontend received

Where feasible calculate:

ingestion latency
Kafka latency
processing latency
broadcast latency
end-to-end latency

Report:

* average
* median/p50
* p95
* optionally p99

Do not claim superiority over commercial brokerages unless independently measured with a valid methodology.

---

# 46. PERFORMANCE ENGINEERING

Use efficient data structures.

Avoid repeatedly querying PostgreSQL for short-lived calculation state.

Use rolling calculations when appropriate.

Use bounded collections.

Avoid memory leaks.

Do not store unlimited tick history in RAM.

Avoid blocking operations in WebSocket callbacks.

Do not use unnecessary synchronization.

Do not create excessive threads.

Prefer batch/asynchronous persistence for non-critical data where appropriate.

---

# 47. MARKET SYMBOL MANAGEMENT

Create a configurable list of supported instruments.

Do not hard-code hundreds of symbols throughout the codebase.

Example:

symbols:

* RELIANCE
* TCS
* INFY
* HDFCBANK
* ICICIBANK

Store symbol metadata in the database where appropriate.

Support adding/removing instruments through configuration or API.

---

# 48. DEMO / SIMULATION FALLBACK

The project must remain demonstrable even if the external market-data API is unavailable during evaluation.

Implement a clearly separated simulation mode.

Example:

DATA_MODE=LIVE
DATA_MODE=SIMULATION

Simulation mode must generate clearly marked synthetic market data.

It must NOT pretend that synthetic values are real market data.

When simulation mode is active, the UI must display:

"DEMO / SIMULATED DATA"

This is important because the final application should not break during a classroom demonstration due to third-party API availability.

---

# 49. SECURITY

Use environment variables for:

* API keys
* DB passwords
* Kafka credentials
* secrets

Never commit secrets.

Provide:

.env.example

Never hard-code real API credentials.

For authentication if implemented:

* hash passwords properly
* never store plaintext passwords
* validate input
* protect endpoints appropriately

Use secure defaults.

---

# 50. CONFIGURATION

Use application configuration files.

Examples:

application.yml

and environment variables.

Make configurable:

* API endpoint
* API key
* Kafka bootstrap server
* Kafka topic
* consumer group
* database URL
* database username/password
* indicator periods
* score weights
* queue size
* thread-pool sizes
* WebSocket endpoint
* simulation/live mode

Do not scatter magic numbers throughout code.

---

# 51. CLEAN CODE REQUIREMENTS

Use:

* meaningful names
* single responsibility
* low coupling
* high cohesion
* interfaces where meaningful
* DTOs
* service classes
* repository abstraction
* configuration classes
* validation
* unit-testable logic

Avoid:

* giant classes
* giant methods
* duplicated logic
* static global state everywhere
* hard-coded API keys
* magic constants
* unnecessary framework complexity

---

# 52. RECOMMENDED BACKEND PACKAGE STRUCTURE

Use a structure similar to:

com.quantstream
│
├── config
├── controller
├── service
├── repository
├── domain
│   ├── model
│   ├── enums
│   └── dto
├── websocket
├── kafka
│   ├── producer
│   └── consumer
├── marketdata
├── analytics
│   ├── indicator
│   ├── scoring
│   └── state
├── concurrency
├── exception
└── util

Adapt where necessary.

---

# 53. REACT / NEXT.JS STRUCTURE

Use a structure similar to:

src/
├── app/
├── components/
│   ├── dashboard/
│   ├── charts/
│   ├── indicators/
│   ├── score/
│   ├── watchlist/
│   └── common/
├── hooks/
├── services/
│   ├── api
│   └── websocket
├── types/
├── utils/
└── store/

Keep UI components reusable.

---

# 54. FRONTEND DESIGN

Use a polished dark financial-analytics aesthetic.

Preferred characteristics:

* dark navy/charcoal background
* subtle cards
* strong typography hierarchy
* green for positive movement
* red for negative movement
* amber for neutral/caution
* blue/cyan for system/data indicators
* clean charts
* rounded cards
* compact tables
* responsive layout

Do not overuse gradients.

Do not make it look like a generic AI-generated dashboard.

The UI should feel like a believable financial analytics product.

---

# 55. MAIN UI SCREENS

Build at minimum:

1. Dashboard
2. Stock detail
3. Quantitative scanner
4. Watchlist
5. Alerts
6. Settings / configuration
7. System status/health if appropriate

Dashboard:

* live watchlist
* top signals
* market summary
* connection status

Stock detail:

* price chart
* indicators
* score
* score breakdown

Scanner:

* ranking table
* sortable/filterable

Alerts:

* generated alert events

Settings:

* selected symbols
* indicator preferences
* alert thresholds
* display preferences

---

# 56. CHARTS

Use a reliable React-compatible charting library.

Support:

* price chart
* volume chart
* indicator lines if feasible
* score history where historical data exists

Charts must update when new data arrives.

Do not reload the whole page.

---

# 57. SCORE GAUGE

Create an attractive Conviction Score gauge.

Example:

CONVICTION SCORE
82 / 100

Also show:

Strong

or an appropriate category.

The gauge must be driven by backend data.

The score breakdown should appear beneath/alongside the gauge.

---

# 58. LIVE CONNECTION INDICATOR

Display:

● LIVE

when connected.

Display:

● RECONNECTING

when trying to reconnect.

Display:

● OFFLINE

when disconnected.

If simulation mode is active:

● DEMO DATA

This makes the system status obvious during demonstration.

---

# 59. API CONTRACTS

Define DTOs between backend and frontend.

Do not expose internal entity models blindly.

Create DTOs such as:

StockDto
MarketUpdateDto
IndicatorSnapshotDto
ConvictionScoreDto
AlertDto
WatchlistDto

Maintain a clean API contract.

---

# 60. TESTING

Write tests for:

Core Java logic
Indicator calculations
Scoring engine
Queue behavior
Market-data parsing
Kafka serialization/deserialization
REST endpoints
WebSocket services
Database repositories
Frontend critical components

Minimum quantitative tests should cover:

Moving average known values
RSI known values
Momentum known values
Relative volume known values
Score calculation known values

The scoring engine must have deterministic tests.

Example:

Given known indicator inputs:

Expected score = known value

Do not test only "not null."

Test actual mathematical correctness.

---

# 61. CONCURRENCY TESTING

Write tests that verify:

* concurrent tick submission
* queue behavior
* correct processing
* no obvious race-condition-induced corruption
* graceful shutdown
* bounded queue behavior

Where practical, use multiple worker threads in tests.

---

# 62. INTEGRATION TESTING

Provide a way to run:

Frontend
Backend
Kafka
PostgreSQL

together.

Use Docker Compose.

Suggested services:

quantstream-backend
quantstream-frontend
kafka
postgres
possibly kafka-ui

Make local development easy.

---

# 63. DOCKER

Create:

Dockerfile for backend
Dockerfile for frontend
docker-compose.yml

Expose appropriate ports.

Example conceptual ports:

Backend:
8080

Frontend:
3000

PostgreSQL:
5432

Kafka:
9092

Adjust as required.

Provide a README explaining startup.

---

# 64. DATABASE MIGRATIONS

Use a migration mechanism such as Flyway or Liquibase if appropriate.

Do not manually require users to create database tables by hand.

The application should initialize its schema predictably.

---

# 65. SEED / DEMO DATA

Provide a way to seed symbols/user/demo configuration.

Do not seed fake "live market data" without marking it.

Demo data should be explicitly identified as demo/simulation data.

---

# 66. README

Write a comprehensive README containing:

Project overview
Architecture diagram
Technology stack
Prerequisites
Setup instructions
Environment variables
Kafka configuration
Database configuration
Running backend
Running frontend
Running simulation mode
Running live mode
API endpoints
WebSocket endpoint
Scoring methodology
Indicators
Testing
Docker instructions
Troubleshooting
Known limitations
Future improvements

Also include a section:

"Why these technologies were chosen"

Explain:

Java
Spring Boot
Kafka
BlockingQueue
ExecutorService
WebSocket
PostgreSQL
React

---

# 67. ARCHITECTURAL DOCUMENTATION

Create a docs/ directory.

Include:

docs/architecture.md
docs/data-flow.md
docs/scoring-model.md
docs/api.md
docs/concurrency.md
docs/kafka.md

Document the full pipeline.

Example:

Market API
→ WebSocket
→ Producer
→ Kafka
→ Consumer
→ Processing Queue
→ In-Memory State
→ Indicators
→ Conviction Score
→ WebSocket
→ React

---

# 68. EXPLAIN THE JAVA CONCEPTS IN CODE

Since this is an FSJP project, the implementation should make Java concepts visible.

The code should clearly demonstrate:

* inheritance where useful
* interfaces
* encapsulation
* composition
* collections
* generics
* exception handling
* concurrency
* thread pools
* blocking queues
* synchronization / concurrent collections
* streams where useful
* enums
* immutable DTOs where appropriate

Do not artificially add design patterns simply for the sake of saying "we used design patterns."

Use patterns only when they improve architecture.

---

# 69. DESIGN PATTERNS WHERE APPROPRIATE

Possible patterns:

Strategy Pattern:
for different indicators/scoring strategies

Factory Pattern:
for market-data providers

Observer / Event-driven model:
for analytics updates

Producer-Consumer:
for streaming processing

Repository Pattern:
for persistence

Service Layer:
for business logic

Adapter Pattern:
for external market-data APIs

Use only those that make sense.

Document them.

---

# 70. DO NOT CREATE A FAKE "MICROSERVICE" ARCHITECTURE

The project does not need dozens of microservices.

A clean modular Spring Boot backend is sufficient.

Kafka can still provide event-streaming architecture.

If services are separated, justify them.

Do not introduce unnecessary complexity purely to sound "enterprise."

---

# 71. DO NOT OVERUSE THE DATABASE

The central architectural idea is:

HOT PATH:

Market Event
→ Stream
→ Java processing
→ In-memory state
→ Indicator
→ Score
→ WebSocket

PERSISTENCE PATH:

Result / user state / historical data
→ PostgreSQL

Do not force:

Market Event
→ PostgreSQL
→ SQL query
→ Indicator

for every tick unless a specific feature genuinely requires it.

---

# 72. DO NOT MAKE THE SYSTEM DATABASE-FREE

PostgreSQL must still be used for persistent data such as:

* users
* watchlists
* configurations
* alerts
* historical snapshots
* other persistent application data

Explain the difference between:

in-memory working state

and

persistent application state.

---

# 73. REAL-TIME UPDATE SEMANTICS

The frontend should receive updates based on newly processed analytical events.

Do not rely on arbitrary periodic frontend polling for the live dashboard.

If a batching or throttling policy is implemented, document it.

For example, if backend processing receives thousands of ticks per second but UI updates are intentionally coalesced, explain why.

Do not pretend that every market tick necessarily must result in a browser repaint.

---

# 74. THROUGHPUT AND BACKPRESSURE

Implement a controlled strategy for high incoming event rates.

Possible mechanisms:

* bounded ArrayBlockingQueue
* Kafka retention
* consumer scaling
* batching
* coalescing latest-state updates where appropriate
* controlled dropping only when mathematically/business appropriate

Never silently drop financial events without documenting the policy.

Distinguish:

* events required for accurate indicator state
* events that can safely be coalesced for UI display

---

# 75. GRACEFUL SHUTDOWN

On application shutdown:

1. Stop accepting new work.
2. Stop market-data subscriptions.
3. Stop WebSocket ingestion.
4. Drain/finish appropriate processing.
5. Close Kafka producers/consumers cleanly.
6. Stop executors.
7. Close database connections gracefully.

Do not leave unmanaged threads running.

---

# 76. RECONNECTION

External market-data connections can fail.

Implement:

* connection detection
* retry delay
* exponential backoff
* maximum retry delay
* resubscription after reconnect
* status propagation to UI

Do not reconnect in a tight infinite loop.

---

# 77. HEALTH CHECKS

Expose backend health.

At minimum verify:

* application
* database
* Kafka
* market-data connection

The frontend can show a system status page.

Example:

Backend      HEALTHY
PostgreSQL   HEALTHY
Kafka        HEALTHY
Market Feed  CONNECTED
WebSocket    CONNECTED

---

# 78. AUDITABILITY OF ANALYTICS

Whenever a Conviction Score is generated, retain enough information to explain the calculation.

For a persisted analytics snapshot, store:

* symbol
* timestamp
* indicator values
* score
* score components
* scoring version if practical

This is useful if the score formula changes later.

Introduce:

SCORING_MODEL_VERSION

where appropriate.

---

# 79. CONFIGURABLE SCORING MODEL

The weights should not be deeply buried in code.

Create configuration like:

scoring:
trend-weight: ...
momentum-weight: ...
volume-weight: ...
rsi-weight: ...

Indicator periods:

indicators:
moving-average-period: 20
rsi-period: 14
momentum-period: 10
volume-lookback: 20

Validate that weights are sensible and normalized as required.

---

# 80. MARKET DATA NORMALIZATION

Different providers may use different field names or structures.

Normalize into your internal:

StockTick

Do not allow provider-specific JSON structures to leak through the entire application.

Example:

Provider Message
↓
Provider Adapter
↓
StockTick
↓
Internal System

This makes the provider replaceable.

---

# 81. USE BIGDECIMAL WHERE APPROPRIATE

For financial prices and monetary values, prefer appropriate precision.

Avoid careless use of floating-point arithmetic for money.

For indicators where floating-point numerical computation is reasonable, use well-defined numeric handling and rounding.

Document rounding decisions.

---

# 82. DATA VALIDATION

Reject or flag invalid market events such as:

price <= 0
missing symbol
missing timestamp
invalid volume
malformed payload

Do not allow invalid events to poison indicator state.

---

# 83. TIME HANDLING

Use a consistent time standard internally.

Prefer:

Instant

for event timestamps where appropriate.

Convert to local display time only in the frontend/UI layer.

---

# 84. FRONTEND DATA FRESHNESS

Show the last update timestamp.

If the data becomes stale, show:

STALE DATA

rather than continuing to imply that the screen is live.

Define a configurable stale-data threshold.

---

# 85. SIMULATION ENGINE

For demonstration, build a simulation source that follows the same internal interface as the live market-data provider.

Architecture:

MarketDataProvider
├── LiveMarketDataProvider
└── SimulatedMarketDataProvider

The simulation should generate:

* price changes
* volume changes
* timestamp
* symbols

Make the simulation deterministic or configurable for testing where practical.

Clearly label simulated data.

---

# 86. PERFORMANCE DASHBOARD

Provide an optional system-performance panel showing:

Ticks received
Ticks processed
Queue depth
Processing latency
WebSocket clients
Kafka status
Average analytics processing time

This helps demonstrate the engineering aspect of the project.

---

# 87. DEMO FLOW

The finished project must support this demonstration:

1. Start infrastructure.
2. Start backend.
3. Start frontend.
4. Choose live or demo mode.
5. Connect to market-data source.
6. Receive ticks.
7. Publish to Kafka.
8. Consume from Kafka.
9. Process using Java.
10. Update in-memory state.
11. Calculate indicators.
12. Calculate Conviction Score.
13. Broadcast result through WebSocket.
14. Update React dashboard.
15. Show ranking.
16. Trigger an alert when threshold conditions are met.
17. Show stored historical data.

The whole chain must work end to end.

---

# 88. SAMPLE END-TO-END FLOW

For a tick:

RELIANCE = ₹2907.20

The system should conceptually do:

1. Provider sends market event.
2. WebSocket client receives it.
3. Provider adapter parses it.
4. StockTick is created.
5. Kafka Producer publishes it.
6. Kafka stores it in market-ticks.
7. Spring Kafka Consumer receives it.
8. Event enters internal processing stage.
9. In-memory state for RELIANCE is updated.
10. Recent price window is updated.
11. Recent volume window is updated.
12. Moving Average is updated.
13. RSI is updated.
14. Momentum is calculated.
15. Relative Volume is calculated.
16. Scoring Engine combines signals.
17. ConvictionScore object is produced.
18. Optional persistence task stores snapshot.
19. WebSocket service broadcasts update.
20. React receives update.
21. Dashboard updates.
22. Ranking updates.
23. Alert engine evaluates conditions.

---

# 89. EXAMPLE RESULT

A possible real output:

{
"symbol": "RELIANCE",
"price": 2907.20,
"priceChangePercent": 1.28,
"rsi": 64.2,
"movingAverage": 2885.1,
"momentum": 1.28,
"relativeVolume": 2.1,
"convictionScore": 82,
"scoreCategory": "VERY_STRONG",
"signals": {
"trend": "POSITIVE",
"momentum": "POSITIVE",
"volume": "ELEVATED",
"rsi": "POSITIVE"
},
"timestamp": "..."
}

Treat this as an example structure.

---

# 90. LIMITATIONS TO ACKNOWLEDGE

Document these honestly:

* Market-data provider limitations
* API rate limits
* Network latency
* Internet dependency
* In-memory state volatility
* Indicator limitations
* Rule-based score limitations
* No guarantee of predictive accuracy
* No brokerage execution
* No direct trading
* Third-party service failures

Acknowledge that the score is an analytical model.

---

# 91. FUTURE EXTENSIONS

Design the project so these can be added later:

* more indicators
* EMA
* MACD
* Bollinger Bands
* VWAP
* backtesting
* historical strategy analysis
* machine-learning models
* sentiment analysis
* anomaly detection
* distributed scaling
* cloud deployment
* advanced alerting
* portfolio analytics

Do not implement all of these unless necessary.

Do not bloat the core project.

---

# 92. IMPORTANT: IMPLEMENT FIRST, DOCUMENT SECOND

Do not create documentation claiming features that do not actually work.

Everything documented as "implemented" must exist in the code.

If a feature cannot be connected to a real external provider during development, clearly implement simulation mode and mark it as simulation.

Do not fabricate live market data.

Do not fabricate benchmark results.

Do not fabricate screenshots.

---

# 93. ACCEPTANCE CRITERIA

The project is complete only if:

1. Backend starts successfully.
2. Frontend starts successfully.
3. PostgreSQL starts successfully.
4. Kafka starts successfully.
5. Backend can connect to Kafka.
6. Backend can connect to the selected market-data source OR simulation mode.
7. Market events become StockTick objects.
8. Events are published into Kafka.
9. Events are consumed successfully.
10. Processing occurs through Java services.
11. In-memory market state updates correctly.
12. Indicators calculate correctly.
13. Conviction Score calculates deterministically.
14. Score breakdown is available.
15. WebSocket delivers analytics results.
16. React receives live updates.
17. Dashboard updates without manual refresh.
18. Scanner ranks stocks.
19. Alerts work.
20. Persistent data is stored in PostgreSQL.
21. Reconnection logic works.
22. Graceful shutdown works.
23. Tests pass.
24. Docker Compose can start the local stack.
25. README accurately describes the system.

---

# 94. REQUIRED DELIVERABLES

Generate:

backend/
frontend/
docs/
docker/
tests/

and appropriate root-level files.

At minimum provide:

README.md
.env.example
docker-compose.yml

Backend:

pom.xml or Gradle equivalent
complete Spring Boot source
tests

Frontend:

package.json
complete React/Next.js source
tests where appropriate

Docs:

architecture.md
data-flow.md
scoring-model.md
concurrency.md
kafka.md
api.md

---

# 95. DEVELOPMENT PRIORITY

When implementing, prioritize:

1. Correct architecture
2. Working data flow
3. Correct Java concurrency
4. Correct quantitative calculations
5. Reliable Kafka integration
6. Correct WebSocket delivery
7. Database persistence
8. Frontend quality
9. Monitoring
10. Documentation

Do not prioritize flashy UI over functioning backend architecture.

---

# 96. EXAMINER-FRIENDLY IMPLEMENTATION

The project will be evaluated by college examiners who may ask:

"What is Java doing here?"

"What is Kafka?"

"Why Kafka and BlockingQueue?"

"Why WebSocket?"

"Why not just REST?"

"Why use in-memory processing?"

"Why PostgreSQL if you're using memory?"

"What happens if Kafka goes down?"

"What happens if the API disconnects?"

"What happens if processing is slower than incoming events?"

"How is the Conviction Score calculated?"

"How do you know the score is correct?"

"How do you measure latency?"

"Why isn't this just another stock app?"

Design the implementation so these questions have clear answers.

---

# 97. VIVA POSITIONING

The project should be explainable as:

"QuantStream is a Java-based real-time quantitative analytics engine. It consumes live market events through WebSocket-based market-data ingestion, uses Kafka to decouple the event stream, processes events concurrently in Java, maintains the recent analytical state in memory, calculates indicators such as Moving Average, RSI, Momentum and Relative Volume, combines those signals into an explainable Conviction Score, and pushes the resulting analytics to a React dashboard through WebSockets. PostgreSQL is used for persistent application and historical data."

Do not describe the project as a brokerage.

Do not claim it replaces Zerodha.

Do not claim it guarantees profits.

Do not claim performance numbers that have not been measured.

---

# 98. FINAL ARCHITECTURAL PRINCIPLE

The most important conceptual flow is:

```
              LIVE MARKET EVENTS
                     ↓
              DATA INGESTION
                     ↓
             EVENT STREAMING
                     ↓
            JAVA CONCURRENCY
                     ↓
            IN-MEMORY STATE
                     ↓
         QUANTITATIVE INDICATORS
                     ↓
            SCORING ENGINE
                     ↓
            EXPLAINABLE RESULT
                     ↓
             WEBSOCKET PUSH
                     ↓
              REACT DASHBOARD
```

Persistent application/history data should use:

```
                     ↓
                PostgreSQL
```

The system must remain modular so individual layers can be replaced without redesigning the entire project.

---

# 99. BUILD EXPECTATION

Do not merely generate pseudocode.

Build the actual working project.

Create the actual files.

Implement the actual classes.

Implement real Spring Boot configuration.

Implement real Kafka producers and consumers.

Implement real WebSocket communication.

Implement real PostgreSQL repositories.

Implement actual indicator formulas.

Implement actual score calculation.

Implement frontend pages and components.

Implement testing.

Implement Docker configuration.

Implement README.

At every point prefer a simple correct implementation over an unnecessarily complex implementation.

If an external service cannot be used in the current environment, implement the proper abstraction and a clearly marked simulation provider so the entire system remains executable.

Do not stop at an architecture diagram or skeleton.

The final result must be a runnable, coherent end-to-end QuantStream application.
