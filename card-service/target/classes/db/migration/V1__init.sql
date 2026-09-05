CREATE TABLE cards (
    card_id BIGSERIAL PRIMARY KEY,
    user_id INTEGER,
    card_number VARCHAR(50) NOT NULL UNIQUE,
    card_type VARCHAR(50),
    expire_date DATE,
    cvv VARCHAR(10),
    card_provider VARCHAR(50),
    status VARCHAR(20) DEFAULT 'ACTIVE',
    credit_limit DECIMAL(15,2),
    points DECIMAL(15,2),
    created_at TIMESTAMP DEFAULT now(), updated_at TIMESTAMP DEFAULT now(), deleted_at TIMESTAMP
);

CREATE TABLE card_auth_transactions (
    auth_txn_id BIGSERIAL PRIMARY KEY,
    card_number VARCHAR(50),
    merchant_id INTEGER,
    amount DECIMAL(15,2),
    currency VARCHAR(10),
    pos_entry_mode VARCHAR(50),
    mcc VARCHAR(10),
    idempotency_key VARCHAR(100) UNIQUE,
    risk_score INTEGER,
    status VARCHAR(20) DEFAULT 'PENDING',
    authorized_at TIMESTAMP, reversed_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT now(), updated_at TIMESTAMP DEFAULT now(), deleted_at TIMESTAMP
);

CREATE INDEX idx_cards_user ON cards(user_id);
CREATE INDEX idx_cards_number ON cards(card_number);
CREATE INDEX idx_auth_card ON card_auth_transactions(card_number);
CREATE TABLE outbox (
    id BIGSERIAL PRIMARY KEY,
    aggregate_type VARCHAR(50) NOT NULL,
    aggregate_id VARCHAR(50) NOT NULL,
    topic VARCHAR(100) NOT NULL,
    payload TEXT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    domain VARCHAR(50),
    event_id VARCHAR(36) NOT NULL UNIQUE,
    attempts INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    processed_at TIMESTAMP,
    last_error TEXT
);
CREATE INDEX idx_outbox_status ON outbox(status, created_at);
