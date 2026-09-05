package com.statswriter.statswriter.consumer;

import com.common.event.EventEnvelope;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Kafka consumer for payment stats events — mirrors Quarkus StatsKafkaConsumer.
 * Consumes 7 stats.payment.* topics, batches, flushes to ClickHouse every 5s / 1000 rows.
 */
@Component
public class StatsWriterConsumer {

    private static final Logger log = LoggerFactory.getLogger(StatsWriterConsumer.class);
    private static final int BATCH_SIZE = 1000;

    private final JdbcTemplate clickhouseJdbc;
    private final ObjectMapper objectMapper;
    private final ConcurrentLinkedQueue<Map<String, Object>> transactionBuffer = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<Map<String, Object>> topupBuffer = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<Map<String, Object>> transferBuffer = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<Map<String, Object>> withdrawBuffer = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<Map<String, Object>> saldoBuffer = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<Map<String, Object>> merchantBuffer = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<Map<String, Object>> cardBuffer = new ConcurrentLinkedQueue<>();

    public StatsWriterConsumer(@Qualifier("clickhouseJdbcTemplate") JdbcTemplate clickhouseJdbc,
                                ObjectMapper objectMapper) {
        this.clickhouseJdbc = clickhouseJdbc;
        this.objectMapper = objectMapper;
    }

    private void buffer(ConcurrentLinkedQueue<Map<String, Object>> q, String message) {
        try {
            EventEnvelope<Map<String, Object>> envelope = objectMapper.readValue(
                message, new TypeReference<EventEnvelope<Map<String, Object>>>() {});
            q.offer(envelope.payload());
        } catch (Exception e) {
            log.error("Failed to deserialize stats event: {}", e.getMessage());
        }
    }

    @KafkaListener(topics = "stats.payment.transaction.event", groupId = "stats-writer")
    public void consumeTransaction(String message) {
        buffer(transactionBuffer, message);
        if (transactionBuffer.size() >= BATCH_SIZE) flush("pos_stats.transaction_events",
            "(event_id, event_version, occurred_at, transaction_id, card_number, amount, payment_method, status) VALUES (?, 1, now(), ?, ?, ?, ?, ?)",
            transactionBuffer, "transactionId", "cardNumber", "amount", "paymentMethod", "status");
    }

    @KafkaListener(topics = "stats.payment.topup.event", groupId = "stats-writer")
    public void consumeTopup(String message) {
        buffer(topupBuffer, message);
        if (topupBuffer.size() >= BATCH_SIZE) flush("pos_stats.topup_events",
            "(event_id, event_version, occurred_at, topup_id, card_number, amount, status) VALUES (?, 1, now(), ?, ?, ?, ?)",
            topupBuffer, "topupId", "cardNumber", "amount", "status");
    }

    @KafkaListener(topics = "stats.payment.transfer.event", groupId = "stats-writer")
    public void consumeTransfer(String message) {
        buffer(transferBuffer, message);
        if (transferBuffer.size() >= BATCH_SIZE) flush("pos_stats.transfer_events",
            "(event_id, event_version, occurred_at, transfer_id, transfer_from, transfer_to, amount, status) VALUES (?, 1, now(), ?, ?, ?, ?, ?)",
            transferBuffer, "transferId", "transferFrom", "transferTo", "amount", "status");
    }

    @KafkaListener(topics = "stats.payment.withdraw.event", groupId = "stats-writer")
    public void consumeWithdraw(String message) {
        buffer(withdrawBuffer, message);
        if (withdrawBuffer.size() >= BATCH_SIZE) flush("pos_stats.withdraw_events",
            "(event_id, event_version, occurred_at, withdraw_id, card_number, amount, status) VALUES (?, 1, now(), ?, ?, ?, ?)",
            withdrawBuffer, "withdrawId", "cardNumber", "amount", "status");
    }

    @KafkaListener(topics = "stats.payment.saldo.event", groupId = "stats-writer")
    public void consumeSaldo(String message) {
        buffer(saldoBuffer, message);
        if (saldoBuffer.size() >= BATCH_SIZE) flush("pos_stats.saldo_events",
            "(event_id, event_version, occurred_at, saldo_id, card_number, total_balance) VALUES (?, 1, now(), ?, ?, ?)",
            saldoBuffer, "saldoId", "cardNumber", "totalBalance");
    }

    @KafkaListener(topics = "stats.payment.merchant.event", groupId = "stats-writer")
    public void consumeMerchant(String message) {
        buffer(merchantBuffer, message);
        if (merchantBuffer.size() >= BATCH_SIZE) flush("pos_stats.merchant_events",
            "(event_id, event_version, occurred_at, merchant_id, name, status) VALUES (?, 1, now(), ?, ?, ?)",
            merchantBuffer, "merchantId", "name", "status");
    }

    @KafkaListener(topics = "stats.payment.card.event", groupId = "stats-writer")
    public void consumeCard(String message) {
        buffer(cardBuffer, message);
        if (cardBuffer.size() >= BATCH_SIZE) flush("pos_stats.card_events",
            "(event_id, event_version, occurred_at, card_id, card_number, user_id, status) VALUES (?, 1, now(), ?, ?, ?, ?)",
            cardBuffer, "cardId", "cardNumber", "userId", "status");
    }

    @Scheduled(fixedDelay = 5000)
    public void scheduledFlush() {
        flush("pos_stats.transaction_events",
            "(event_id, event_version, occurred_at, transaction_id, card_number, amount, payment_method, status) VALUES (?, 1, now(), ?, ?, ?, ?, ?)",
            transactionBuffer, "transactionId", "cardNumber", "amount", "paymentMethod", "status");
        flush("pos_stats.topup_events",
            "(event_id, event_version, occurred_at, topup_id, card_number, amount, status) VALUES (?, 1, now(), ?, ?, ?, ?)",
            topupBuffer, "topupId", "cardNumber", "amount", "status");
        flush("pos_stats.transfer_events",
            "(event_id, event_version, occurred_at, transfer_id, transfer_from, transfer_to, amount, status) VALUES (?, 1, now(), ?, ?, ?, ?, ?)",
            transferBuffer, "transferId", "transferFrom", "transferTo", "amount", "status");
        flush("pos_stats.withdraw_events",
            "(event_id, event_version, occurred_at, withdraw_id, card_number, amount, status) VALUES (?, 1, now(), ?, ?, ?, ?)",
            withdrawBuffer, "withdrawId", "cardNumber", "amount", "status");
        flush("pos_stats.saldo_events",
            "(event_id, event_version, occurred_at, saldo_id, card_number, total_balance) VALUES (?, 1, now(), ?, ?, ?)",
            saldoBuffer, "saldoId", "cardNumber", "totalBalance");
        flush("pos_stats.merchant_events",
            "(event_id, event_version, occurred_at, merchant_id, name, status) VALUES (?, 1, now(), ?, ?, ?)",
            merchantBuffer, "merchantId", "name", "status");
        flush("pos_stats.card_events",
            "(event_id, event_version, occurred_at, card_id, card_number, user_id, status) VALUES (?, 1, now(), ?, ?, ?, ?)",
            cardBuffer, "cardId", "cardNumber", "userId", "status");
    }

    private void flush(String table, String columns, ConcurrentLinkedQueue<Map<String, Object>> q, String... keys) {
        if (q.isEmpty()) return;
        List<Map<String, Object>> batch = new ArrayList<>();
        Map<String, Object> item;
        while ((item = q.poll()) != null) batch.add(item);
        try {
            for (Map<String, Object> row : batch) {
                StringBuilder sb = new StringBuilder("INSERT INTO ").append(table).append(' ').append(columns);
                List<Object> args = new ArrayList<>();
                args.add(row.get("eventId"));
                for (String k : keys) args.add(row.get(k));
                clickhouseJdbc.update(sb.toString(), args.toArray());
            }
            log.info("Flushed {} events to {}", batch.size(), table);
        } catch (Exception e) {
            log.error("Failed to flush {}: {}", table, e.getMessage());
        }
    }
}