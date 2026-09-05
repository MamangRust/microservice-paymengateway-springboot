package com.topupservice.topupservice.repository;

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

import com.topupservice.topupservice.entity.Outbox;
import com.topupservice.topupservice.entity.OutboxStatus;
import com.topupservice.topupservice.entity.Status;
import com.topupservice.topupservice.entity.Topup;

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
class TopupRepositoryTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");

    @Autowired
    private TopupRepository topupRepository;

    @Autowired
    private OutboxRepository outboxRepository;

    private Topup createTopup(String cardNumber, Integer amount) {
        Topup topup = new Topup();
        topup.setCardNumber(cardNumber);
        topup.setTopupAmount(amount);
        topup.setTopupMethod("BANK_TRANSFER");
        return topup;
    }

    // ---- TopupRepository ----

    @Test
    void save_persistsTopupWithGeneratedIdAndDefaults() {
        Topup saved = topupRepository.save(createTopup("C-001", 50000));

        assertThat(saved.getTopupId()).isNotNull();
        // entity-level defaults: topupNo UUID and Status.PENDING
        assertThat(saved.getTopupNo()).isNotBlank();
        assertThat(saved.getStatus()).isEqualTo(Status.PENDING);
        // @PrePersist timestamps
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
        assertThat(saved.getTopupTime()).isNull();
    }

    @Test
    void save_assignsUniqueTopupNoPerEntity() {
        Topup t1 = topupRepository.save(createTopup("C-001", 50000));
        Topup t2 = topupRepository.save(createTopup("C-002", 100000));

        assertThat(t1.getTopupNo()).isNotEqualTo(t2.getTopupNo());
    }

    @Test
    void findById_returnsSavedTopup() {
        Topup saved = topupRepository.save(createTopup("C-001", 50000));

        Optional<Topup> found = topupRepository.findById(saved.getTopupId());

        assertThat(found).isPresent();
        assertThat(found.get().getCardNumber()).isEqualTo("C-001");
        assertThat(found.get().getTopupAmount()).isEqualTo(50000);
        assertThat(found.get().getStatus()).isEqualTo(Status.PENDING);
    }

    @Test
    void findById_returnsEmptyWhenMissing() {
        assertThat(topupRepository.findById(999999L)).isEmpty();
    }

    @Test
    void findByIdempotencyKey_returnsMatchingTopup() {
        Topup topup = createTopup("C-001", 50000);
        topup.setIdempotencyKey("idem-abc");
        topupRepository.save(topup);

        Optional<Topup> found = topupRepository.findByIdempotencyKey("idem-abc");

        assertThat(found).isPresent();
        assertThat(found.get().getCardNumber()).isEqualTo("C-001");
    }

    @Test
    void findByIdempotencyKey_returnsEmptyWhenMissing() {
        assertThat(topupRepository.findByIdempotencyKey("nope")).isEmpty();
    }

    @Test
    void idempotencyKey_uniqueIndex_rejectsDuplicateLiveRows() {
        Topup first = createTopup("C-001", 50000);
        first.setIdempotencyKey("idem-dup");
        topupRepository.saveAndFlush(first);

        Topup second = createTopup("C-002", 100000);
        second.setIdempotencyKey("idem-dup");

        assertThatThrownBy(() -> {
            topupRepository.save(second);
            topupRepository.flush();
        }).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void update_touchesUpdatedAtViaPreUpdate() {
        Topup saved = topupRepository.save(createTopup("C-001", 50000));
        LocalDateTime createdAtBefore = saved.getCreatedAt();

        saved.setTopupAmount(100000);
        Topup updated = topupRepository.saveAndFlush(saved);

        assertThat(updated.getTopupAmount()).isEqualTo(100000);
        assertThat(updated.getCreatedAt()).isEqualTo(createdAtBefore);
        assertThat(updated.getUpdatedAt()).isNotNull();
    }

    @Test
    void deleteById_removesRow() {
        Topup saved = topupRepository.save(createTopup("C-001", 50000));

        topupRepository.deleteById(saved.getTopupId());
        topupRepository.flush();

        assertThat(topupRepository.findById(saved.getTopupId())).isEmpty();
    }

    // ---- OutboxRepository ----

    private Outbox createOutbox(String eventId, OutboxStatus status) {
        Outbox outbox = new Outbox();
        outbox.setAggregateType("Topup");
        outbox.setAggregateId("1");
        outbox.setTopic("stats.payment.topup.event");
        outbox.setPayload("{\"eventType\":\"topup.created\"}");
        outbox.setStatus(status);
        outbox.setDomain("topup");
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
