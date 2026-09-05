-- ClickHouse init schema — pos_stats (payment gateway analytics)
CREATE DATABASE IF NOT EXISTS pos_stats;

CREATE TABLE IF NOT EXISTS pos_stats.transaction_events (
    event_id String, event_version UInt64, occurred_at DateTime,
    transaction_id UInt64, card_number String, amount UInt64, payment_method String, status String
) ENGINE = ReplacingMergeTree(event_version) PARTITION BY toYYYYMM(occurred_at) ORDER BY (transaction_id);

CREATE TABLE IF NOT EXISTS pos_stats.topup_events (
    event_id String, event_version UInt64, occurred_at DateTime,
    topup_id UInt64, card_number String, amount UInt64, status String
) ENGINE = ReplacingMergeTree(event_version) PARTITION BY toYYYYMM(occurred_at) ORDER BY (topup_id);

CREATE TABLE IF NOT EXISTS pos_stats.transfer_events (
    event_id String, event_version UInt64, occurred_at DateTime,
    transfer_id UInt64, transfer_from String, transfer_to String, amount UInt64, status String
) ENGINE = ReplacingMergeTree(event_version) PARTITION BY toYYYYMM(occurred_at) ORDER BY (transfer_id);

CREATE TABLE IF NOT EXISTS pos_stats.withdraw_events (
    event_id String, event_version UInt64, occurred_at DateTime,
    withdraw_id UInt64, card_number String, amount UInt64, status String
) ENGINE = ReplacingMergeTree(event_version) PARTITION BY toYYYYMM(occurred_at) ORDER BY (withdraw_id);

CREATE TABLE IF NOT EXISTS pos_stats.saldo_events (
    event_id String, event_version UInt64, occurred_at DateTime,
    saldo_id UInt64, card_number String, total_balance UInt64
) ENGINE = ReplacingMergeTree(event_version) PARTITION BY toYYYYMM(occurred_at) ORDER BY (saldo_id);

CREATE TABLE IF NOT EXISTS pos_stats.merchant_events (
    event_id String, event_version UInt64, occurred_at DateTime,
    merchant_id UInt64, name String, status String
) ENGINE = ReplacingMergeTree(event_version) PARTITION BY toYYYYMM(occurred_at) ORDER BY (merchant_id);

CREATE TABLE IF NOT EXISTS pos_stats.card_events (
    event_id String, event_version UInt64, occurred_at DateTime,
    card_id UInt64, card_number String, user_id UInt64, status String
) ENGINE = ReplacingMergeTree(event_version) PARTITION BY toYYYYMM(occurred_at) ORDER BY (card_id);