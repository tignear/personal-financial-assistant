CREATE TABLE portfolio (
    id SERIAL PRIMARY KEY,
    payload JSONB NOT NULL,
    created_at TIMESTAMPTZ DEFAULT now(),
    user_id VARCHAR(64) NOT NULL,
    CONSTRAINT portfolio_unique UNIQUE (user_id)
);
CREATE UNIQUE INDEX idx_portfolio_unique ON portfolio(user_id);