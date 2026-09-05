package com.statsbackfill.statsbackfill.job;

import com.common.event.EventEnvelope;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Reads orders from order-db and transactions from transaction-db,
 * publishes events to Kafka topics so stats-writer replays them into ClickHouse.
 */
@Component
public class BackfillLifecycle implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(BackfillLifecycle.class);

    private final JdbcTemplate orderJdbc;
    private final JdbcTemplate transactionJdbc;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Value("${backfill.from:2020-01-01}")
    private String backfillFrom;

    public BackfillLifecycle(@Qualifier("orderJdbcTemplate") JdbcTemplate orderJdbc,
                             @Qualifier("transactionJdbcTemplate") JdbcTemplate transactionJdbc,
                             KafkaTemplate<String, Object> kafkaTemplate,
                             ObjectMapper objectMapper) {
        this.orderJdbc = orderJdbc;
        this.transactionJdbc = transactionJdbc;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        LocalDate from = LocalDate.parse(backfillFrom);
        log.info("Starting stats backfill from {}", from);
        backfillOrders(from);
        backfillTransactions(from);
        log.info("Stats backfill completed");
        System.exit(0);
    }

    private void backfillOrders(LocalDate from) {
        List<Map<String, Object>> orders = orderJdbc.queryForList(
            "SELECT id, product_id, user_id, quantity, payment_status, created_at " +
            "FROM orders WHERE created_at >= ?", from);
        log.info("Backfilling {} orders", orders.size());
        for (Map<String, Object> o : orders) {
            var payload = Map.of(
                "eventId", UUID.randomUUID().toString(),
                "orderId", o.get("id").toString(),
                "productId", o.get("product_id") == null ? "" : o.get("product_id").toString(),
                "userId", o.get("user_id") == null ? "" : o.get("user_id").toString(),
                "quantity", o.get("quantity"),
                "status", o.get("payment_status") == null ? "UNKNOWN" : o.get("payment_status").toString()
            );
            EventEnvelope<Map<String, Object>> envelope =
                EventEnvelope.withDefaults(payload, "order.completed", "order");
            try {
                kafkaTemplate.send("stats.ecommerce.order.event", o.get("id").toString(),
                    objectMapper.writeValueAsString(envelope));
            } catch (Exception e) {
                log.error("Failed to publish order event: {}", e.getMessage());
            }
        }
    }

    private void backfillTransactions(LocalDate from) {
        List<Map<String, Object>> txns = transactionJdbc.queryForList(
            "SELECT transaction_id, order_id, merchant_id, payment_method, amount, status, created_at " +
            "FROM transactions WHERE created_at >= ?", from);
        log.info("Backfilling {} transactions", txns.size());
        for (Map<String, Object> t : txns) {
            var payload = Map.of(
                "eventId", UUID.randomUUID().toString(),
                "transactionId", t.get("transaction_id"),
                "orderId", t.get("order_id"),
                "merchantId", t.get("merchant_id"),
                "paymentMethod", t.get("payment_method") == null ? "" : t.get("payment_method"),
                "status", t.get("status"),
                "amount", t.get("amount")
            );
            EventEnvelope<Map<String, Object>> envelope =
                EventEnvelope.withDefaults(payload, "transaction.completed", "transaction");
            try {
                kafkaTemplate.send("stats.ecommerce.transaction.event", String.valueOf(t.get("transaction_id")),
                    objectMapper.writeValueAsString(envelope));
            } catch (Exception e) {
                log.error("Failed to publish transaction event: {}", e.getMessage());
            }
        }
    }
}