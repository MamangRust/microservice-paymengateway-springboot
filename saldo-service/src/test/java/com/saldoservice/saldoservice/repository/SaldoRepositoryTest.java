package com.saldoservice.saldoservice.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
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

import com.saldoservice.saldoservice.entity.Outbox;
import com.saldoservice.saldoservice.entity.OutboxStatus;
import com.saldoservice.saldoservice.entity.Saldo;
import com.saldoservice.saldoservice.entity.SaldoMutationOperation;

/**
 * One class, three repositories: all share the same Flyway schema (V1__init.sql)
 * and the same PostgreSQL container, so splitting them would just triple the
 * container startup cost.
 */
@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.enabled=true"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
class SaldoRepositoryTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");

    @Autowired
    private SaldoRepository saldoRepository;

    @Autowired
    private SaldoMutationOperationRepository mutationRepository;

    @Autowired
    private OutboxRepository outboxRepository;

    private Saldo createSaldo(String cardNumber, Integer totalBalance) {
        Saldo saldo = new Saldo();
        saldo.setCardNumber(cardNumber);
        saldo.setTotalBalance(totalBalance);
        return saldo;
    }

    private SaldoMutationOperation createOperation(String operationKey, String cardNumber) {
        SaldoMutationOperation op = new SaldoMutationOperation();
        op.setOperationKey(operationKey);
        op.setCardNumber(cardNumber);
        op.setRequestedDelta(50);
        op.setMinimumBalance(0);
        op.setResultStatus("SUCCESS");
        op.setResultBalance(150);
        return op;
    }

    // ---- SaldoRepository ----

    @Test
    void save_persistsSaldoWithGeneratedIdAndDefaults() {
        Saldo saved = saldoRepository.save(createSaldo("C-001", 100));

        // PK field is named saldoId
        assertThat(saved.getSaldoId()).isNotNull();
        assertThat(saved.getTotalBalance()).isEqualTo(100);
        assertThat(saved.getWithdrawAmount()).isNull();
        assertThat(saved.getWithdrawTime()).isNull();
        // @PrePersist timestamps
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
        assertThat(saved.getDeletedAt()).isNull();
    }

    @Test
    void save_withoutTotalBalance_persistsEntityDefaultZero() {
        // entity initializer totalBalance = 0 only applies while the field is untouched
        Saldo saldo = new Saldo();
        saldo.setCardNumber("C-000");

        Saldo saved = saldoRepository.save(saldo);

        assertThat(saved.getSaldoId()).isNotNull();
        assertThat(saved.getTotalBalance()).isZero();
    }

    @Test
    void findById_returnsSavedSaldo() {
        Saldo saved = saldoRepository.save(createSaldo("C-001", 100));

        Optional<Saldo> found = saldoRepository.findById(saved.getSaldoId());

        assertThat(found).isPresent();
        assertThat(found.get().getCardNumber()).isEqualTo("C-001");
        assertThat(found.get().getTotalBalance()).isEqualTo(100);
    }

    @Test
    void findById_returnsEmptyWhenMissing() {
        assertThat(saldoRepository.findById(999999L)).isEmpty();
    }

    @Test
    void findByCardNumber_returnsMatchingSaldo() {
        saldoRepository.save(createSaldo("C-001", 100));
        saldoRepository.save(createSaldo("C-002", 200));

        Optional<Saldo> found = saldoRepository.findByCardNumber("C-001");

        assertThat(found).isPresent();
        assertThat(found.get().getTotalBalance()).isEqualTo(100);
    }

    @Test
    void findByCardNumber_returnsEmptyWhenMissing() {
        saldoRepository.save(createSaldo("C-001", 100));

        assertThat(saldoRepository.findByCardNumber("nope")).isEmpty();
    }

    @Test
    void cardNumber_uniqueIndex_rejectsDuplicateLiveRows() {
        saldoRepository.saveAndFlush(createSaldo("C-001", 100));

        assertThatThrownBy(() -> {
            saldoRepository.save(createSaldo("C-001", 500));
            saldoRepository.flush();
        }).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void update_touchesUpdatedAtViaPreUpdate() {
        Saldo saved = saldoRepository.save(createSaldo("C-001", 100));
        LocalDateTime createdAtBefore = saved.getCreatedAt();

        saved.setTotalBalance(150);
        Saldo updated = saldoRepository.saveAndFlush(saved);

        assertThat(updated.getTotalBalance()).isEqualTo(150);
        assertThat(updated.getCreatedAt()).isEqualTo(createdAtBefore);
        assertThat(updated.getUpdatedAt()).isNotNull();
    }

    @Test
    void deleteById_removesRow() {
        Saldo saved = saldoRepository.save(createSaldo("C-001", 100));

        saldoRepository.deleteById(saved.getSaldoId());
        saldoRepository.flush();

        assertThat(saldoRepository.findById(saved.getSaldoId())).isEmpty();
    }

    // ---- SaldoMutationOperationRepository ----

    @Test
    void saveOperation_persistsWithAssignedStringPrimaryKey() {
        SaldoMutationOperation saved = mutationRepository.save(createOperation("op-1", "C-001"));

        assertThat(saved.getOperationKey()).isEqualTo("op-1");
        assertThat(saved.getResultStatus()).isEqualTo("SUCCESS");
        assertThat(saved.getResultBalance()).isEqualTo(150);
        // @PrePersist timestamps
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
    }

    @Test
    void findByOperationKey_returnsMatchingOperation() {
        mutationRepository.save(createOperation("op-abc", "C-001"));

        Optional<SaldoMutationOperation> found = mutationRepository.findByOperationKey("op-abc");

        assertThat(found).isPresent();
        assertThat(found.get().getRequestedDelta()).isEqualTo(50);
        assertThat(found.get().getCardNumber()).isEqualTo("C-001");
    }

    @Test
    void findByOperationKey_returnsEmptyWhenMissing() {
        assertThat(mutationRepository.findByOperationKey("nope")).isEmpty();
    }

    @Test
    void updateOperation_overwritesFieldsUnderSameKey() {
        mutationRepository.save(createOperation("op-1", "C-001"));

        SaldoMutationOperation existing = mutationRepository.findByOperationKey("op-1").orElseThrow();
        existing.setResultStatus("FAILED");
        existing.setFailureReason("Insufficient balance");
        mutationRepository.saveAndFlush(existing);

        SaldoMutationOperation reloaded = mutationRepository.findByOperationKey("op-1").orElseThrow();
        assertThat(reloaded.getResultStatus()).isEqualTo("FAILED");
        assertThat(reloaded.getFailureReason()).isEqualTo("Insufficient balance");
    }

    // ---- OutboxRepository ----

    private Outbox createOutbox(String eventId, OutboxStatus status) {
        Outbox outbox = new Outbox();
        outbox.setAggregateType("Saldo");
        outbox.setAggregateId("1");
        outbox.setTopic("stats.payment.saldo.event");
        outbox.setPayload("{\"eventType\":\"saldo.created\"}");
        outbox.setStatus(status);
        outbox.setDomain("saldo");
        outbox.setEventId(eventId);
        return outbox;
    }

    @Test
    void saveOutbox_persistsWithGeneratedIdAndDefaults() {
        Outbox saved = outboxRepository.save(createOutbox("evt-1", OutboxStatus.PENDING));

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(saved.getAttempts()).isEqualTo(0);
        // @PrePersist fills created_at when not set explicitly
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getProcessedAt()).isNull();
        assertThat(saved.getLastError()).isNull();
    }

    @Test
    void findByStatusOrderByCreatedAt_returnsOnlyPendingSortedAscending() {
        // @PrePersist overwrites createdAt on insert, so distinct timestamps
        // must be applied via an UPDATE after the rows are persisted.
        Outbox p1 = outboxRepository.save(createOutbox("evt-pending-1", OutboxStatus.PENDING));
        Outbox p2 = outboxRepository.save(createOutbox("evt-pending-2", OutboxStatus.PENDING));
        Outbox p3 = outboxRepository.save(createOutbox("evt-pending-3", OutboxStatus.PENDING));
        outboxRepository.save(createOutbox("evt-processed", OutboxStatus.PROCESSED));

        LocalDateTime base = LocalDateTime.of(2026, 9, 4, 10, 0);
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
        outboxRepository.save(createOutbox("evt-processed-2", OutboxStatus.PROCESSED));

        assertThat(outboxRepository.findByStatusOrderByCreatedAt(OutboxStatus.PENDING)).isEmpty();
    }
}
