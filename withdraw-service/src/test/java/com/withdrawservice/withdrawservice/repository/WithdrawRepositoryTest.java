package com.withdrawservice.withdrawservice.repository;

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

import com.withdrawservice.withdrawservice.entity.Outbox;
import com.withdrawservice.withdrawservice.entity.OutboxStatus;
import com.withdrawservice.withdrawservice.entity.Withdraw;

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
class WithdrawRepositoryTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");

    @Autowired
    private WithdrawRepository withdrawRepository;

    @Autowired
    private OutboxRepository outboxRepository;

    private Withdraw withdraw(String cardNumber, Integer amount, String idempotencyKey) {
        Withdraw w = new Withdraw();
        w.setCardNumber(cardNumber);
        w.setWithdrawAmount(amount);
        w.setIdempotencyKey(idempotencyKey);
        return w;
    }

    private Outbox outbox(String eventId, OutboxStatus status) {
        Outbox row = new Outbox();
        row.setAggregateType("Withdraw");
        row.setAggregateId("1");
        row.setTopic("stats.payment.withdraw.event");
        row.setPayload("{\"eventType\":\"withdraw.created\"}");
        row.setStatus(status);
        row.setDomain("withdraw");
        row.setEventId(eventId);
        return row;
    }

    // ---- WithdrawRepository ----

    @Test
    void save_persistsWithdrawWithGeneratedIdAndDefaults() {
        Withdraw saved = withdrawRepository.save(withdraw("4111111111111111", 250000, null));

        assertThat(saved.getWithdrawId()).isNotNull();
        // entity-level defaults
        assertThat(saved.getStatus()).isEqualTo(com.withdrawservice.withdrawservice.entity.Status.PENDING);
        assertThat(saved.getWithdrawNo()).isNotBlank();
        // @PrePersist timestamps
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
        assertThat(saved.getDeletedAt()).isNull();
    }

    @Test
    void save_generatesDistinctWithdrawNoPerRow() {
        Withdraw first = withdrawRepository.save(withdraw("4111111111111111", 250000, null));
        Withdraw second = withdrawRepository.save(withdraw("4222222222222222", 99000, null));

        assertThat(first.getWithdrawNo()).isNotEqualTo(second.getWithdrawNo());
    }

    @Test
    void findById_returnsSavedWithdraw() {
        Withdraw saved = withdrawRepository.save(withdraw("4111111111111111", 250000, null));

        Optional<Withdraw> found = withdrawRepository.findById(saved.getWithdrawId());

        assertThat(found).isPresent();
        assertThat(found.get().getCardNumber()).isEqualTo("4111111111111111");
        assertThat(found.get().getWithdrawAmount()).isEqualTo(250000);
    }

    @Test
    void findById_returnsEmptyWhenMissing() {
        assertThat(withdrawRepository.findById(999999L)).isEmpty();
    }

    @Test
    void findByIdempotencyKey_returnsMatchingWithdraw() {
        withdrawRepository.save(withdraw("4111111111111111", 50000, "idem-abc"));

        Optional<Withdraw> found = withdrawRepository.findByIdempotencyKey("idem-abc");

        assertThat(found).isPresent();
        assertThat(found.get().getWithdrawAmount()).isEqualTo(50000);
    }

    @Test
    void findByIdempotencyKey_returnsEmptyWhenMissing() {
        assertThat(withdrawRepository.findByIdempotencyKey("nope")).isEmpty();
    }

    @Test
    void idempotencyKey_uniqueConstraint_rejectsDuplicate() {
        withdrawRepository.saveAndFlush(withdraw("4111111111111111", 50000, "idem-dup"));

        // quirk: plain UNIQUE on idempotency_key — a soft-deleted row would still block reuse
        assertThatThrownBy(() -> withdrawRepository.saveAndFlush(withdraw("4222222222222222", 60000, "idem-dup")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void update_touchesUpdatedAtViaPreUpdate() {
        Withdraw saved = withdrawRepository.save(withdraw("4111111111111111", 50000, null));

        saved.setStatus(com.withdrawservice.withdrawservice.entity.Status.SUCCESS);
        Withdraw updated = withdrawRepository.saveAndFlush(saved);

        assertThat(updated.getStatus()).isEqualTo(com.withdrawservice.withdrawservice.entity.Status.SUCCESS);
        assertThat(updated.getUpdatedAt()).isNotNull();
    }

    @Test
    void deleteById_removesRow() {
        Withdraw saved = withdrawRepository.save(withdraw("4111111111111111", 50000, null));

        withdrawRepository.deleteById(saved.getWithdrawId());
        withdrawRepository.flush();

        assertThat(withdrawRepository.findById(saved.getWithdrawId())).isEmpty();
    }

    // ---- OutboxRepository ----

    @Test
    void save_persistsOutboxWithGeneratedIdAndDefaults() {
        Outbox saved = outboxRepository.save(outbox("evt-1", OutboxStatus.PENDING));

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(saved.getAttempts()).isZero();
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
