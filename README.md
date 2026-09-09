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
3.  **Apache Kafka & Zookeeper** (Running on default ports: Zookeeper `2181`, Kafka `9092`)
4.  **PostgreSQL** (Running on `localhost:5432`)

### Database Setup

Create a database named `quantstream` in your local PostgreSQL instance:

```sql
CREATE DATABASE quantstream;
```

Update your database credentials in `backend/src/main/resources/application.properties`:
```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/quantstream
spring.datasource.username=your_username
spring.datasource.password=your_password
```

## How to Run Locally

You need to run both the Backend and the Frontend servers simultaneously.

### 1. Start the Backend (Spring Boot)

Open a terminal in the `backend` directory.

**Option A: Run in Simulation Mode (No API key required)**
This mode generates synthetic market data.
```bash
cd backend
$env:MARKET_DATA_MODE="simulation"
.\mvnw.cmd spring-boot:run
```

**Option B: Run in Live Mode (Finnhub API key required)**
This mode streams live US equity data.
```bash
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

## Accessing the Dashboard

Once both servers are running, open your web browser and navigate to:
**http://localhost:3000**
