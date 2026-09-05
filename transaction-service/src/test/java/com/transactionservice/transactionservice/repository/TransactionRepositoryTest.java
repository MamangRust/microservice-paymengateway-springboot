package com.transactionservice.transactionservice.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.transactionservice.transactionservice.entity.Outbox;
import com.transactionservice.transactionservice.entity.OutboxStatus;
import com.transactionservice.transactionservice.entity.Transaction;

/**
 * One class, two repositories: both share the same Flyway schema (V1__init.sql)
 * and the same PostgreSQL container, so splitting them would just double the
 * container startup cost.
 */
@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.enabled=true"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
class TransactionRepositoryTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private OutboxRepository outboxRepository;

    private Transaction transaction(String cardNumber, Integer amount, String idempotencyKey) {
        Transaction txn = new Transaction();
        txn.setCardNumber(cardNumber);
        txn.setAmount(amount);
        txn.setPaymentMethod("QRIS");
        txn.setMerchantId(1);
        txn.setIdempotencyKey(idempotencyKey);
        return txn;
    }

    private Outbox outbox(String eventId, OutboxStatus status) {
        Outbox row = new Outbox();
        row.setAggregateType("Transaction");
        row.setAggregateId("1");
        row.setTopic("stats.payment.transaction.event");
        row.setPayload("{\"eventType\":\"transaction.created\"}");
        row.setStatus(status);
        row.setDomain("transaction");
        row.setEventId(eventId);
        return row;
    }

    // ---- TransactionRepository ----

    @Test
    void save_persistsTransactionWithGeneratedIdAndDefaults() {
        Transaction saved = transactionRepository.save(transaction("4111111111111111", 111000, null));

        assertThat(saved.getTransactionId()).isNotNull();
        // entity-level defaults
        assertThat(saved.getStatus()).isEqualTo(com.transactionservice.transactionservice.entity.Status.PENDING);
        assertThat(saved.getTransactionNo()).isNotBlank();
        // @PrePersist timestamps
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
        assertThat(saved.getDeletedAt()).isNull();
    }

    @Test
    void save_generatesDistinctTransactionNoPerRow() {
        // transactionNo is initialized in the entity field, so two saves must not collide
        Transaction first = transactionRepository.save(transaction("4111111111111111", 111000, null));
        Transaction second = transactionRepository.save(transaction("4222222222222222", 222000, null));

        assertThat(first.getTransactionNo()).isNotEqualTo(second.getTransactionNo());
    }

    @Test
    void findById_returnsSavedTransaction() {
        Transaction saved = transactionRepository.save(transaction("4111111111111111", 111000, null));

        Optional<Transaction> found = transactionRepository.findById(saved.getTransactionId());

        assertThat(found).isPresent();
        assertThat(found.get().getCardNumber()).isEqualTo("4111111111111111");
        assertThat(found.get().getAmount()).isEqualTo(111000);
        assertThat(found.get().getMerchantId()).isEqualTo(1);
    }

    @Test
    void findById_returnsEmptyWhenMissing() {
        assertThat(transactionRepository.findById(999999L)).isEmpty();
    }

    @Test
    void findByIdempotencyKey_returnsMatchingTransaction() {
        transactionRepository.save(transaction("4111111111111111", 50000, "idem-abc"));

        Optional<Transaction> found = transactionRepository.findByIdempotencyKey("idem-abc");

        assertThat(found).isPresent();
        assertThat(found.get().getAmount()).isEqualTo(50000);
    }

    @Test
    void findByIdempotencyKey_returnsEmptyWhenMissing() {
        assertThat(transactionRepository.findByIdempotencyKey("nope")).isEmpty();
    }

    @Test
    void idempotencyKey_uniqueConstraint_rejectsDuplicate() {
        transactionRepository.saveAndFlush(transaction("4111111111111111", 50000, "idem-dup"));

        // quirk: V1__init.sql defines a plain UNIQUE on idempotency_key. Rows carry a
        // deleted_at column but nothing sets it, and a soft-deleted row would still block
        // key reuse — there is no partial index like "WHERE deleted_at IS NULL".
        assertThatThrownBy(() -> transactionRepository.saveAndFlush(transaction("4222222222222222", 60000, "idem-dup")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void update_touchesUpdatedAtViaPreUpdate() {
        Transaction saved = transactionRepository.save(transaction("4111111111111111", 50000, null));

        saved.setStatus(com.transactionservice.transactionservice.entity.Status.SUCCESS);
        Transaction updated = transactionRepository.saveAndFlush(saved);

        assertThat(updated.getStatus()).isEqualTo(com.transactionservice.transactionservice.entity.Status.SUCCESS);
        assertThat(updated.getUpdatedAt()).isNotNull();
    }

    @Test
    void deleteById_removesRow() {
        Transaction saved = transactionRepository.save(transaction("4111111111111111", 50000, null));

        transactionRepository.deleteById(saved.getTransactionId());
        transactionRepository.flush();

        assertThat(transactionRepository.findById(saved.getTransactionId())).isEmpty();
    }

    // ---- OutboxRepository ----

    @Test
    void save_persistsOutboxWithGeneratedIdAndDefaults() {
        Outbox saved = outboxRepository.save(outbox("evt-1", OutboxStatus.PENDING));

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(saved.getAttempts()).isZero();
        // @PrePersist fills created_at when not set explicitly
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getProcessedAt()).isNull();
        assertThat(saved.getLastError()).isNull();
    }

    @Test
    void findByStatusOrderByCreatedAt_returnsOnlyMatchingStatusSortedAscending() {
        // @PrePersist overwrites createdAt on insert, so distinct timestamps
        // must be applied via an UPDATE after the rows are persisted.
        Outbox p1 = outboxRepository.save(outbox("evt-pending-1", OutboxStatus.PENDING));
        Outbox p2 = outboxRepository.save(outbox("evt-pending-2", OutboxStatus.PENDING));
        Outbox p3 = outboxRepository.save(outbox("evt-pending-3", OutboxStatus.PENDING));
        outboxRepository.save(outbox("evt-processed", OutboxStatus.PROCESSED));

        java.time.LocalDateTime base = java.time.LocalDateTime.of(2026, 9, 4, 10, 0);
        p1.setCreatedAt(base);
        p2.setCreatedAt(base.plusMinutes(5));
        p3.setCreatedAt(base.minusMinutes(5));
        outboxRepository.saveAllAndFlush(List.of(p1, p2, p3));

        List<Outbox> result = outboxRepository.findByStatusOrderByCreatedAt(OutboxStatus.PENDING);

        assertThat(result).hasSize(3);
        assertThat(result).extracting(Outbox::getEventId)
                .containsExactly("evt-pending-3", "evt-pending-1", "evt-pending-2");
    }

    @Test
    void findByStatusOrderByCreatedAt_returnsEmptyWhenNoPending() {
        outboxRepository.save(outbox("evt-processed-2", OutboxStatus.PROCESSED));

        assertThat(outboxRepository.findByStatusOrderByCreatedAt(OutboxStatus.PENDING)).isEmpty();
    }
}
