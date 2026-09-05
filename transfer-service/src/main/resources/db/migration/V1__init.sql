CREATE TABLE transfers (
    transfer_id BIGSERIAL PRIMARY KEY,
    transfer_no VARCHAR(36) NOT NULL UNIQUE,
    transfer_from VARCHAR(50),
    transfer_to VARCHAR(50),
    transfer_amount INTEGER,
    transfer_time TIMESTAMP,
    idempotency_key VARCHAR(100) UNIQUE,
    request_fingerprint VARCHAR(100),
    status VARCHAR(30) DEFAULT 'PENDING',
    created_at TIMESTAMP DEFAULT now(), updated_at TIMESTAMP DEFAULT now(), deleted_at TIMESTAMP
);
CREATE INDEX idx_transfer_from ON transfers(transfer_from);
CREATE INDEX idx_transfer_to ON transfers(transfer_to);
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
