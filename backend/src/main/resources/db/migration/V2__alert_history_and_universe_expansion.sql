-- QuantStream V2: Alert History and Simulation Universe Expansion

ALTER TABLE alert_configs
ADD COLUMN IF NOT EXISTS triggered BOOLEAN NOT NULL DEFAULT FALSE,
ADD COLUMN IF NOT EXISTS triggered_at TIMESTAMP WITH TIME ZONE,
ADD COLUMN IF NOT EXISTS triggered_value NUMERIC(12, 4);

CREATE TABLE IF NOT EXISTS alert_trigger_history (
    id BIGSERIAL PRIMARY KEY,
    alert_id BIGINT REFERENCES alert_configs(id) ON DELETE CASCADE,
    symbol VARCHAR(20) NOT NULL,
    condition_type VARCHAR(50) NOT NULL,
    threshold NUMERIC(12, 4) NOT NULL,
    triggered_value NUMERIC(12, 4) NOT NULL,
    triggered_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_alert_history_symbol ON alert_trigger_history (symbol, triggered_at DESC);
CREATE INDEX IF NOT EXISTS idx_alert_history_alert ON alert_trigger_history (alert_id, triggered_at DESC);

-- Populate all 40 realistic NSE simulation instruments
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
    ('LT', 'Larsen & Toubro Ltd', 'NSE', true),
    ('HINDUNILVR', 'Hindustan Unilever Ltd', 'NSE', true),
    ('AXISBANK', 'Axis Bank Ltd', 'NSE', true),
    ('MARUTI', 'Maruti Suzuki India Ltd', 'NSE', true),
    ('SUNPHARMA', 'Sun Pharmaceutical Industries Ltd', 'NSE', true),
    ('TATAMOTORS', 'Tata Motors Ltd', 'NSE', true),
    ('BAJFINANCE', 'Bajaj Finance Ltd', 'NSE', true),
    ('ASIANPAINT', 'Asian Paints Ltd', 'NSE', true),
    ('TITAN', 'Titan Company Ltd', 'NSE', true),
    ('WIPRO', 'Wipro Ltd', 'NSE', true),
    ('ULTRACEMCO', 'UltraTech Cement Ltd', 'NSE', true),
    ('NTPC', 'NTPC Ltd', 'NSE', true),
    ('ONGC', 'Oil & Natural Gas Corporation Ltd', 'NSE', true),
    ('POWERGRID', 'Power Grid Corporation of India Ltd', 'NSE', true),
    ('MM', 'Mahindra & Mahindra Ltd', 'NSE', true),
    ('ADANIENT', 'Adani Enterprises Ltd', 'NSE', true),
    ('ADANIPORTS', 'Adani Ports and Special Economic Zone Ltd', 'NSE', true),
    ('COALINDIA', 'Coal India Ltd', 'NSE', true),
    ('TATASTEEL', 'Tata Steel Ltd', 'NSE', true),
    ('JSWSTEEL', 'JSW Steel Ltd', 'NSE', true),
    ('HCLTECH', 'HCL Technologies Ltd', 'NSE', true),
    ('BAJAJFINSV', 'Bajaj Finserv Ltd', 'NSE', true),
    ('TECHM', 'Tech Mahindra Ltd', 'NSE', true),
    ('INDUSINDBK', 'IndusInd Bank Ltd', 'NSE', true),
    ('NESTLEIND', 'Nestle India Ltd', 'NSE', true),
    ('GRASIM', 'Grasim Industries Ltd', 'NSE', true),
    ('CIPLA', 'Cipla Ltd', 'NSE', true),
    ('DRREDDY', 'Dr. Reddy''s Laboratories Ltd', 'NSE', true),
    ('EICHERMOT', 'Eicher Motors Ltd', 'NSE', true),
    ('APOLLOHOSP', 'Apollo Hospitals Enterprise Ltd', 'NSE', true),
    ('BPCL', 'Bharat Petroleum Corporation Ltd', 'NSE', true)
ON CONFLICT (symbol) DO NOTHING;
