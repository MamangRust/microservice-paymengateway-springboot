CREATE TABLE saldos (
    saldo_id BIGSERIAL PRIMARY KEY,
    card_number VARCHAR(50) NOT NULL UNIQUE,
    total_balance INTEGER DEFAULT 0,
    withdraw_amount INTEGER,
    withdraw_time TIMESTAMP,
    created_at TIMESTAMP DEFAULT now(), updated_at TIMESTAMP DEFAULT now(), deleted_at TIMESTAMP
);

CREATE TABLE saldo_mutation_operations (
    operation_key VARCHAR(100) PRIMARY KEY,
    card_number VARCHAR(50) NOT NULL,
    requested_delta INTEGER,
    minimum_balance INTEGER,
    result_status VARCHAR(20),
    result_balance INTEGER,
    failure_reason TEXT,
    created_at TIMESTAMP DEFAULT now(), updated_at TIMESTAMP DEFAULT now(), deleted_at TIMESTAMP
);

CREATE INDEX idx_saldo_card ON saldos(card_number);
CREATE INDEX idx_mutation_card ON saldo_mutation_operations(card_number);
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
