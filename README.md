# QuantStream

QuantStream is an institutional-grade, real-time quantitative market data streaming pipeline and analytics engine. It features a high-throughput Spring Boot backend utilizing WebSockets, Kafka, and a concurrent in-memory state engine, paired with a modern Next.js React frontend.

## Architecture

*   **Backend:** Spring Boot (Java), STOMP/SockJS WebSockets, Apache Kafka, PostgreSQL.
*   **Frontend:** Next.js (React), Custom CSS Data-Terminal UI.
*   **Modes:** 
    *   **Live Mode:** Connects to the Finnhub API for real-time US Equity ticks.
    *   **Simulation Mode:** Deterministic synthetic feed using geometric Brownian motion for offline testing.

## Prerequisites

Before running the application, ensure you have the following installed:

1.  **Java 17+** (for the Spring Boot backend)
2.  **Node.js (v18+) & npm** (for the Next.js frontend)
3.  **Apache Kafka (KRaft mode)** or Docker (Kafka on port `9092`)
4.  **PostgreSQL** (Running on `localhost:5432`)

### Quick Start with Docker Compose (Recommended)

To spin up the complete stack (PostgreSQL, Kafka KRaft, Spring Boot backend, and Next.js frontend) in one command:

```bash
docker compose up --build
```

Access the frontend at `http://localhost:3000` and the backend at `http://localhost:8080`.

---

### Database Setup (For Manual Local Run)

Create a database named `quantstream` in your local PostgreSQL instance:

```sql
CREATE DATABASE quantstream;
```

Update your database credentials in `backend/src/main/resources/application.yml` (or set environment variables `SPRING_DATASOURCE_USERNAME` and `SPRING_DATASOURCE_PASSWORD`):
```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/quantstream
    username: your_username
    password: your_password
```

## How to Run Locally

You need to run both the Backend and the Frontend servers simultaneously.

### 1. Start the Backend (Spring Boot)

Open a terminal in the `backend` directory.

**Option A: Run in Simulation Mode (No API key required)**
This mode generates synthetic market data.

*macOS / Linux:*
```bash
cd backend
export MARKET_DATA_MODE="simulation"
./mvnw spring-boot:run
```

*Windows (PowerShell):*
```powershell
cd backend
$env:MARKET_DATA_MODE="simulation"
.\mvnw.cmd spring-boot:run
```

**Option B: Run in Live Mode (Finnhub API key required)**
This mode streams live US equity data.

*macOS / Linux:*
```bash
cd backend
export MARKET_DATA_MODE="live"
export FINNHUB_API_KEY="your_actual_api_key_here"
./mvnw spring-boot:run
```

*Windows (PowerShell):*
```powershell
cd backend
$env:MARKET_DATA_MODE="live"
$env:FINNHUB_API_KEY="your_actual_api_key_here"
.\mvnw.cmd spring-boot:run
```

*Note: The backend runs on `http://localhost:8080` by default.*

### 2. Start the Frontend (Next.js)

Open a *second* terminal window in the `frontend` directory.

```bash
cd frontend
npm install
npm run dev
```

*Note: The frontend runs on `http://localhost:3000` by default.*

Once both servers are running, open your web browser and navigate to:
**http://localhost:3000**

---

## Supported Market Universes

QuantStream enforces strict universe validation against a fixed instrument registry:

*   **Simulation Mode Universe (40 NSE Equities):**
    *   **Banking & Financials:** `RELIANCE`, `HDFCBANK`, `ICICIBANK`, `SBIN`, `KOTAKBANK`, `AXISBANK`, `BAJFINANCE`, `BAJAJFINSV`
    *   **IT & Tech:** `TCS`, `INFY`, `HCLTECH`, `WIPRO`, `TECHM`, `LTIM`
    *   **Automotive:** `TATAMOTORS`, `MARUTI`, `M&M`, `BAJAJ-AUTO`
    *   **Energy & Utilities:** `NTPC`, `POWERGRID`, `ONGC`, `COALINDIA`, `BPCL`
    *   **FMCG & Retail:** `ITC`, `HINDUNILVR`, `TITAN`, `NESTLEIND`, `ASIANPAINT`
    *   **Metals & Infrastructure:** `LT`, `TATASTEEL`, `JSWSTEEL`, `HINDALCO`, `ADANIENT`, `ADANIPORTS`, `ULTRACEMCO`, `GRASIM`
    *   **Pharma & Healthcare:** `SUNPHARMA`, `CIPLA`, `DRREDDY`, `APOLLOHOSP`
*   **Live Mode Universe (7 US Equities via Finnhub):**
    *   `AAPL`, `MSFT`, `GOOGL`, `AMZN`, `NVDA`, `TSLA`, `META`

Curated subset on Dashboard features 10 benchmark equities (`RELIANCE`, `TCS`, `HDFCBANK`, `INFY`, `ICICIBANK`, `SBIN`, `BHARTIARTL`, `ITC`, `LT`, `TATAMOTORS`), while Watchlist, Scanner, and Search query the complete 40-symbol universe.

---

## Continuous Conviction Scoring Engine

QuantStream calculates a continuous **0–100** quantitative conviction score combining four deterministic technical indicators with equal weighting ($0.25$ each):

$$\text{Final Score} = 0.25 \times \text{Trend} + 0.25 \times \text{Momentum} + 0.25 \times \text{RSI} + 0.25 \times \text{Relative Volume}$$

### Factor Normalization Formulas & Thresholds

1.  **Trend Score (0–100):**
    Measures percentage divergence of the current price from the 20-period Simple Moving Average (SMA):
    $$\Delta\% = \frac{\text{Price} - \text{SMA}_{20}}{\text{SMA}_{20}} \times 100$$
    $$\text{Trend Score} = \text{clamp}\left(50.0 + \left(\frac{\Delta\%}{2.0\%}\right) \times 50.0,\; 0.0,\; 100.0\right)$$
    *A divergence of $\pm 2.0\%$ maps continuously to $[0.0, 100.0]$ with $0.0\%$ divergence at $50.0$ (neutral).*

2.  **Momentum Score (0–100):**
    Measures 10-period price rate of change:
    $$\text{ROC}\% = \frac{\text{Price}_t - \text{Price}_{t-10}}{\text{Price}_{t-10}} \times 100$$
    $$\text{Momentum Score} = \text{clamp}\left(50.0 + \left(\frac{\text{ROC}\%}{2.0\%}\right) \times 50.0,\; 0.0,\; 100.0\right)$$

3.  **RSI Score (0–100):**
    Direct Wilder 14-period Relative Strength Index:
    $$\text{RSI Score} = \text{clamp}(\text{RSI}_{14},\; 0.0,\; 100.0)$$

4.  **Relative Volume Score (0–100):**
    Measures volume ratio relative to 20-period average volume:
    $$\text{RVOL} = \frac{\text{Volume}}{\text{AvgVolume}_{20}}$$
    $$\text{Volume Score} = \text{clamp}(\text{RVOL} \times 50.0,\; 0.0,\; 100.0)$$
    *Baseline $1.0\times$ volume maps to $50.0$; elevated $2.0\times$ volume maps to $100.0$.*

### Score Explainability
All factor scores and contributions are rounded to 1 decimal place:
*   Example: Trend `78.0` (contrib `19.5`), Momentum `64.0` (contrib `16.0`), RSI `71.0` (contrib `17.8`), Volume `83.0` (contrib `20.8`) $\to$ **Conviction Score: `74.0`**.

---

## Real-Time Alert Engine & Lifecycle

*   **Supported Alert Types:** `PRICE_ABOVE`, `PRICE_BELOW`, `CONVICTION_ABOVE` (or `SCORE_ABOVE`), `CONVICTION_BELOW` (or `SCORE_BELOW`).
*   **One-Shot Trigger Semantics:** Alerts transition to `TRIGGERED` exactly once upon crossing the threshold to prevent duplicate event spamming. Users can re-arm triggered alerts via the UI or `PUT /api/alerts/{id}/reset`.
*   **End-to-End Pipeline:**
    $$\text{Incoming Tick} \longrightarrow \text{Worker Pool} \longrightarrow \text{Alert Evaluator} \longrightarrow \text{PostgreSQL Audit Log} \longrightarrow \text{STOMP /topic/alerts} \longrightarrow \text{Frontend Modal/Toast}$$
*   **Audit History:** Every trigger records the trigger timestamp, exact trigger value, and threshold crossed in `alert_trigger_history`.
