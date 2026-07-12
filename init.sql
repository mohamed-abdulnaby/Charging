-- ============================================================
-- Prepaid Voice Charging – Database Initialisation
-- ============================================================

CREATE TABLE IF NOT EXISTS Users (
    id      SERIAL PRIMARY KEY,
    msisdn  VARCHAR(20) UNIQUE NOT NULL,
    balance NUMERIC(10, 2) NOT NULL DEFAULT 100.0
);

-- Sample subscriber data
INSERT INTO Users (msisdn, balance) VALUES ('201001234567', 100.00) ON CONFLICT DO NOTHING;
INSERT INTO Users (msisdn, balance) VALUES ('201009876543', 50.00)  ON CONFLICT DO NOTHING;
INSERT INTO Users (msisdn, balance) VALUES ('201005551234', 200.00) ON CONFLICT DO NOTHING;

CREATE TABLE IF NOT EXISTS CDRs (
    id SERIAL PRIMARY KEY,
    msisdn VARCHAR(20) NOT NULL,
    start_time TIMESTAMP NOT NULL,
    end_time TIMESTAMP NOT NULL,
    duration_mins INT NOT NULL,
    cost NUMERIC(10, 2) NOT NULL,
    result VARCHAR(100) NOT NULL,
    final_balance NUMERIC(10, 2) NOT NULL
);
