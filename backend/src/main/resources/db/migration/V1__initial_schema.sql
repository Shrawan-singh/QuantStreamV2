-- QuantStream V1 Initial Schema

CREATE TABLE IF NOT EXISTS symbols (
    symbol VARCHAR(20) PRIMARY KEY,
    company_name VARCHAR(100) NOT NULL,
    exchange VARCHAR(20) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS watchlist_items (
    id BIGSERIAL PRIMARY KEY,
    symbol VARCHAR(20) NOT NULL UNIQUE,
    added_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    notes VARCHAR(255)
);

CREATE TABLE IF NOT EXISTS alert_configs (
    id BIGSERIAL PRIMARY KEY,
    symbol VARCHAR(20) NOT NULL,
    condition_type VARCHAR(50) NOT NULL,
    threshold NUMERIC(12, 4) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

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

CREATE INDEX IF NOT EXISTS idx_snapshot_symbol_time ON analytics_snapshots (symbol, timestamp DESC);

-- Initial seed data
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
