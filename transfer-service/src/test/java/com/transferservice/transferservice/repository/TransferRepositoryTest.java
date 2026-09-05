package com.transferservice.transferservice.repository;

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

import com.transferservice.transferservice.entity.Outbox;
import com.transferservice.transferservice.entity.OutboxStatus;
import com.transferservice.transferservice.entity.Transfer;

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
class TransferRepositoryTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");

    @Autowired
    private TransferRepository transferRepository;

    @Autowired
    private OutboxRepository outboxRepository;

    private Transfer transfer(String from, String to, Integer amount, String idempotencyKey) {
        Transfer t = new Transfer();
        t.setTransferFrom(from);
        t.setTransferTo(to);
        t.setTransferAmount(amount);
        t.setIdempotencyKey(idempotencyKey);
        return t;
    }

    private Outbox outbox(String eventId, OutboxStatus status) {
        Outbox row = new Outbox();
        row.setAggregateType("Transfer");
        row.setAggregateId("1");
        row.setTopic("stats.payment.transfer.event");
        row.setPayload("{\"eventType\":\"transfer.created\"}");
        row.setStatus(status);
        row.setDomain("transfer");
        row.setEventId(eventId);
        return row;
    }

    // ---- TransferRepository ----

    @Test
    void save_persistsTransferWithGeneratedIdAndDefaults() {
        Transfer saved = transferRepository.save(transfer("ACC-001", "ACC-002", 250000, null));

        assertThat(saved.getTransferId()).isNotNull();
        // entity-level defaults
        assertThat(saved.getStatus()).isEqualTo(com.transferservice.transferservice.entity.Status.PENDING);
        assertThat(saved.getTransferNo()).isNotBlank();
        // @PrePersist timestamps
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
        assertThat(saved.getDeletedAt()).isNull();
    }

    @Test
    void save_generatesDistinctTransferNoPerRow() {
        Transfer first = transferRepository.save(transfer("ACC-001", "ACC-002", 250000, null));
        Transfer second = transferRepository.save(transfer("ACC-003", "ACC-004", 99000, null));

        assertThat(first.getTransferNo()).isNotEqualTo(second.getTransferNo());
    }

    @Test
    void findById_returnsSavedTransfer() {
        Transfer saved = transferRepository.save(transfer("ACC-001", "ACC-002", 250000, null));

        Optional<Transfer> found = transferRepository.findById(saved.getTransferId());

        assertThat(found).isPresent();
        assertThat(found.get().getTransferFrom()).isEqualTo("ACC-001");
        assertThat(found.get().getTransferTo()).isEqualTo("ACC-002");
        assertThat(found.get().getTransferAmount()).isEqualTo(250000);
    }

    @Test
    void findById_returnsEmptyWhenMissing() {
        assertThat(transferRepository.findById(999999L)).isEmpty();
    }

    @Test
    void findByIdempotencyKey_returnsMatchingTransfer() {
        transferRepository.save(transfer("ACC-001", "ACC-002", 50000, "idem-abc"));

        Optional<Transfer> found = transferRepository.findByIdempotencyKey("idem-abc");

        assertThat(found).isPresent();
        assertThat(found.get().getTransferAmount()).isEqualTo(50000);
    }

    @Test
    void findByIdempotencyKey_returnsEmptyWhenMissing() {
        assertThat(transferRepository.findByIdempotencyKey("nope")).isEmpty();
    }

    @Test
    void idempotencyKey_uniqueConstraint_rejectsDuplicate() {
        transferRepository.saveAndFlush(transfer("ACC-001", "ACC-002", 50000, "idem-dup"));

        // quirk: plain UNIQUE on idempotency_key — a soft-deleted row would still block reuse
        assertThatThrownBy(() -> transferRepository.saveAndFlush(transfer("ACC-003", "ACC-004", 60000, "idem-dup")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void update_touchesUpdatedAtViaPreUpdate() {
        Transfer saved = transferRepository.save(transfer("ACC-001", "ACC-002", 50000, null));

        saved.setStatus(com.transferservice.transferservice.entity.Status.SUCCESS);
        Transfer updated = transferRepository.saveAndFlush(saved);

        assertThat(updated.getStatus()).isEqualTo(com.transferservice.transferservice.entity.Status.SUCCESS);
        assertThat(updated.getUpdatedAt()).isNotNull();
    }

    @Test
    void deleteById_removesRow() {
        Transfer saved = transferRepository.save(transfer("ACC-001", "ACC-002", 50000, null));

        transferRepository.deleteById(saved.getTransferId());
        transferRepository.flush();

        assertThat(transferRepository.findById(saved.getTransferId())).isEmpty();
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
