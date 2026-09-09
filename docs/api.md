# QuantStream REST and WebSocket API Reference

## 1. REST Endpoints

All endpoints are hosted under `/api`.

### Stocks API

#### `GET /api/stocks`
Returns a list of all actively monitored symbols with their latest analytics snapshot.

**Sample Response (`200 OK`)**:
```json
[
  {
    "symbol": "RELIANCE",
    "companyName": "Reliance Industries Ltd",
    "price": 2907.20,
    "previousPrice": 2904.50,
    "priceChange": 2.70,
    "priceChangePercent": 0.09,
    "openPrice": 2895.00,
    "highPrice": 2912.00,
    "lowPrice": 2890.00,
    "volume": 75000,
    "cumulativeVolume": 4500000,
    "sma": 2898.40,
    "ema": 2902.10,
    "rsi": 64.2,
    "momentum": 1.25,
    "relativeVolume": 1.85,
    "convictionScore": 82.5,
    "scoreCategory": "VERY_STRONG",
    "scoreBreakdown": {
      "trend": 25.0,
      "momentum": 25.0,
      "rsi": 25.0,
      "volume": 25.0
    },
    "signals": {
      "trend": "POSITIVE",
      "momentum": "POSITIVE",
      "rsi": "POSITIVE",
      "volume": "POSITIVE"
    },
    "explanations": [
      "Trend: POSITIVE (Price ₹2907.20 vs SMA ₹2898.40)",
      "Momentum: POSITIVE (Lookback change: +1.25%)",
      "RSI: POSITIVE (14-period index: 64.2)",
      "Volume: Elevated (1.85x of 20-period baseline)"
    ],
    "ready": true,
    "source": "SIMULATION",
    "timestamp": "2026-09-07T00:30:00Z"
  }
]
```

#### `GET /api/stocks/{symbol}`
Returns the detailed analytics snapshot for a single symbol.

#### `GET /api/stocks/{symbol}/history`
Returns up to 50 recent historical snapshots for charting and trajectory inspection.

---

### Analytics & Scanner API

#### `GET /api/analytics/scanner?limit=10&category=STRONG`
Returns stocks ranked by conviction score descending with optional category filtering.

#### `GET /api/analytics/summary`
Returns overall market advance/decline statistics and average conviction score.

---

### Watchlist API

#### `GET /api/watchlist`
Retrieves user watchlist instruments paired with their latest real-time snapshots.

#### `POST /api/watchlist`
Adds an instrument to the watchlist.
```json
{
  "symbol": "INFY",
  "notes": "Tech growth benchmark"
}
```

#### `DELETE /api/watchlist/{symbol}`
Removes an instrument from the watchlist.

---

### Alerts API

#### `GET /api/alerts`
Lists all user-configured price and conviction score alerts.

#### `POST /api/alerts`
Creates an alert rule.
```json
{
  "symbol": "RELIANCE",
  "conditionType": "PRICE_ABOVE",
  "threshold": 3000.00,
  "enabled": true
}
```

#### `PUT /api/alerts/{id}/toggle`
Toggles an alert's active status.

#### `DELETE /api/alerts/{id}`
Deletes an alert rule.

---

### Health & Config API

#### `GET /api/health`
Exposes engine runtime health, queue buffer utilization, total processed ticks, and worker pool state.

#### `GET /api/config`
Returns active indicator lookbacks, thresholds, and scoring weights.

---

## 2. WebSocket Real-Time Stream

- **Protocol**: STOMP over SockJS
- **Connection Endpoint**: `http://localhost:8080/ws`
- **Topics**:
  - `/topic/market/all`: Universal broadcast of every analytical update across all instruments.
  - `/topic/market/{SYMBOL}`: Filtered stream for a single instrument (e.g. `/topic/market/RELIANCE`).
