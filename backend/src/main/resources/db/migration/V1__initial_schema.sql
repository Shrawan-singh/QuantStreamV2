-- ==================================================================================
-- FLYWAY DATABASE MIGRATION: V1__initial_schema.sql
-- ==================================================================================
--
-- WHAT IS FLYWAY?
-- Flyway is a database version control tool. Instead of manually clicking in pgAdmin
-- or running raw scripts, Flyway tracks migration versions (V1, V2, etc.) and
-- automatically executes them in order when the application boots up.
--
-- WHAT THIS SCRIPT CREATES:
-- 1. `symbols`: Master directory of supported ticker symbols.
-- 2. `watchlist_items`: User's pinned favorite stocks.
-- 3. `alert_configs`: Rules for price and conviction alerts.
-- 4. `analytics_snapshots`: Historical table of price & indicator values over time.
-- 5. Seed data: Inserts 10 baseline Indian stocks and initial watchlist items.
-- ==================================================================================

-- Table 1: Supported Symbols
CREATE TABLE IF NOT EXISTS symbols (
    symbol VARCHAR(20) PRIMARY KEY,
    company_name VARCHAR(100) NOT NULL,
    exchange VARCHAR(20) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Table 2: User Watchlist Items
CREATE TABLE IF NOT EXISTS watchlist_items (
    id BIGSERIAL PRIMARY KEY,
    symbol VARCHAR(20) NOT NULL UNIQUE,
    added_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    notes VARCHAR(255)
);

-- Table 3: User Alert Rules
CREATE TABLE IF NOT EXISTS alert_configs (
    id BIGSERIAL PRIMARY KEY,
    symbol VARCHAR(20) NOT NULL,
    condition_type VARCHAR(50) NOT NULL,
    threshold NUMERIC(12, 4) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Table 4: Historical Analytical Snapshots (for charting)
CREATE TABLE IF NOT EXISTS analytics_snapshots (
    id BIGSERIAL PRIMARY KEY,
    symbol VARCHAR(20) NOT NULL,
    price NUMERIC(12, 4) NOT NULL,
    sma DOUBLE PRECISION,
    ema DOUBLE PRECISION,
    rsi DOUBLE PRECISION,
    momentum DOUBLE PRECISION,
    relative_volume DOUBLE PRECISION,
    conviction_score DOUBLE PRECISION,
    score_category VARCHAR(30),
    timestamp TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Database index to make querying the last 50 snapshots for a stock lightning fast!
CREATE INDEX IF NOT EXISTS idx_snapshot_symbol_time ON analytics_snapshots (symbol, timestamp DESC);

-- ==================================================================================
-- INITIAL SEED DATA
-- ==================================================================================
INSERT INTO symbols (symbol, company_name, exchange, active) VALUES
    ('RELIANCE', 'Reliance Industries Ltd', 'NSE', true),
    ('TCS', 'Tata Consultancy Services Ltd', 'NSE', true),
    ('INFY', 'Infosys Ltd', 'NSE', true),
    ('HDFCBANK', 'HDFC Bank Ltd', 'NSE', true),
    ('ICICIBANK', 'ICICI Bank Ltd', 'NSE', true),
    ('SBIN', 'State Bank of India', 'NSE', true),
    ('BHARTIARTL', 'Bharti Airtel Ltd', 'NSE', true),
    ('ITC', 'ITC Ltd', 'NSE', true),
    ('KOTAKBANK', 'Kotak Mahindra Bank Ltd', 'NSE', true),
    ('LT', 'Larsen & Toubro Ltd', 'NSE', true)
ON CONFLICT (symbol) DO NOTHING;

INSERT INTO watchlist_items (symbol, notes) VALUES
    ('RELIANCE', 'Core energy & retail holding'),
    ('TCS', 'IT bellwether'),
    ('INFY', 'Tech growth benchmark')
ON CONFLICT (symbol) DO NOTHING;
