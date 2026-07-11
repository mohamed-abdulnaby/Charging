
-- psql -U postgres -d charging -f create_users_table.sql

CREATE TABLE IF NOT EXISTS users (
    msisdn  VARCHAR(20) PRIMARY KEY,
    balance NUMERIC(10,2) NOT NULL DEFAULT 0
);


INSERT INTO users (msisdn, balance) VALUES
    ('01012345678', 50.00),
    ('01098765432', 120.75)
ON CONFLICT (msisdn) DO NOTHING;
