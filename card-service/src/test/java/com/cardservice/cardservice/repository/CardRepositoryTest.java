package com.cardservice.cardservice.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
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

import com.cardservice.cardservice.entity.AuthStatus;
import com.cardservice.cardservice.entity.Card;
import com.cardservice.cardservice.entity.CardAuthTransaction;
import com.cardservice.cardservice.entity.CardStatus;
import com.cardservice.cardservice.entity.Outbox;
import com.cardservice.cardservice.entity.OutboxStatus;

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
class CardRepositoryTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");

    @Autowired
    private CardRepository cardRepository;

    @Autowired
    private CardAuthTransactionRepository authTxnRepository;

    @Autowired
    private OutboxRepository outboxRepository;

    private Card createCard(Integer userId, String cardNumber) {
        Card card = new Card();
        card.setUserId(userId);
        card.setCardNumber(cardNumber);
        card.setCardType("CREDIT");
        card.setExpireDate(LocalDate.of(2027, 12, 31));
        card.setCvv("123");
        card.setCardProvider("VISA");
        card.setCreditLimit(new BigDecimal("5000.00"));
        card.setPoints(new BigDecimal("10.00"));
        return card;
    }

    private CardAuthTransaction createAuthTxn(String cardNumber, String idempotencyKey) {
        CardAuthTransaction txn = new CardAuthTransaction();
        txn.setCardNumber(cardNumber);
        txn.setMerchantId(10);
        txn.setAmount(new BigDecimal("250.00"));
        txn.setCurrency("IDR");
        txn.setPosEntryMode("CHIP");
        txn.setMcc("5411");
        txn.setIdempotencyKey(idempotencyKey);
        txn.setRiskScore(20);
        return txn;
    }

    // ---- CardRepository ----

    @Test
    void save_persistsCardWithGeneratedIdAndDefaults() {
        Card saved = cardRepository.save(createCard(1, "4111111111111111"));

        assertThat(saved.getCardId()).isNotNull();
        // entity-level defaults
        assertThat(saved.getStatus()).isEqualTo(CardStatus.ACTIVE);
        // @PrePersist timestamps
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
        assertThat(saved.getDeletedAt()).isNull();
    }

    @Test
    void findById_returnsSavedCard() {
        Card saved = cardRepository.save(createCard(1, "4222222222222222"));

        Optional<Card> found = cardRepository.findById(saved.getCardId());

        assertThat(found).isPresent();
        assertThat(found.get().getCardNumber()).isEqualTo("4222222222222222");
        assertThat(found.get().getUserId()).isEqualTo(1);
        assertThat(found.get().getCreditLimit()).isEqualByComparingTo("5000.00");
    }

    @Test
    void findById_returnsEmptyWhenMissing() {
        assertThat(cardRepository.findById(999999L)).isEmpty();
    }

    @Test
    void findByCardNumber_returnsMatchingCard() {
        cardRepository.save(createCard(1, "4111111111111111"));
        cardRepository.save(createCard(2, "4222222222222222"));

        Optional<Card> found = cardRepository.findByCardNumber("4111111111111111");

        assertThat(found).isPresent();
        assertThat(found.get().getUserId()).isEqualTo(1);
    }

    @Test
    void findByCardNumber_returnsEmptyWhenMissing() {
        cardRepository.save(createCard(1, "4111111111111111"));

        assertThat(cardRepository.findByCardNumber("nope")).isEmpty();
    }

    @Test
    void findByUserId_returnsOnlyThatUsersCards() {
        cardRepository.save(createCard(7, "4111111111111111"));
        cardRepository.save(createCard(7, "4222222222222222"));
        cardRepository.save(createCard(8, "4333333333333333"));

        List<Card> result = cardRepository.findByUserId(7);

        assertThat(result).hasSize(2);
        assertThat(result).allSatisfy(card -> assertThat(card.getUserId()).isEqualTo(7));
    }

    @Test
    void findByUserId_returnsEmptyWhenNoMatch() {
        cardRepository.save(createCard(1, "4111111111111111"));

        assertThat(cardRepository.findByUserId(42)).isEmpty();
    }

    @Test
    void update_touchesUpdatedAtViaPreUpdate() {
        Card saved = cardRepository.save(createCard(1, "4111111111111111"));
        LocalDateTime createdAtBefore = saved.getCreatedAt();

        saved.setCardType("DEBIT");
        Card updated = cardRepository.saveAndFlush(saved);

        assertThat(updated.getCardType()).isEqualTo("DEBIT");
        assertThat(updated.getCreatedAt()).isEqualTo(createdAtBefore);
        assertThat(updated.getUpdatedAt()).isNotNull();
    }

    @Test
    void deleteById_removesRow() {
        Card saved = cardRepository.save(createCard(1, "4111111111111111"));

        cardRepository.deleteById(saved.getCardId());
        cardRepository.flush();

        assertThat(cardRepository.findById(saved.getCardId())).isEmpty();
    }

    // ---- CardAuthTransactionRepository ----

    @Test
    void saveAuthTxn_persistsWithGeneratedIdAndDefaultStatus() {
        CardAuthTransaction saved = authTxnRepository.save(createAuthTxn("4111111111111111", "idem-1"));

        assertThat(saved.getAuthTxnId()).isNotNull();
        assertThat(saved.getStatus()).isEqualTo(AuthStatus.PENDING);
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
        assertThat(saved.getAuthorizedAt()).isNull();
        assertThat(saved.getReversedAt()).isNull();
    }

    @Test
    void findAuthTxnByCardNumber_returnsOnlyThatCard() {
        authTxnRepository.save(createAuthTxn("4111111111111111", "idem-1"));
        authTxnRepository.save(createAuthTxn("4111111111111111", "idem-2"));
        authTxnRepository.save(createAuthTxn("4222222222222222", "idem-3"));

        List<CardAuthTransaction> result = authTxnRepository.findByCardNumber("4111111111111111");

        assertThat(result).hasSize(2);
        assertThat(result).allSatisfy(txn -> assertThat(txn.getCardNumber()).isEqualTo("4111111111111111"));
    }

    @Test
    void findAuthTxnByCardNumber_returnsEmptyWhenNoMatch() {
        authTxnRepository.save(createAuthTxn("4111111111111111", "idem-1"));

        assertThat(authTxnRepository.findByCardNumber("nope")).isEmpty();
    }

    @Test
    void findByIdempotencyKey_returnsMatchingTxn() {
        authTxnRepository.save(createAuthTxn("4111111111111111", "idem-abc"));

        Optional<CardAuthTransaction> found = authTxnRepository.findByIdempotencyKey("idem-abc");

        assertThat(found).isPresent();
        assertThat(found.get().getCardNumber()).isEqualTo("4111111111111111");
    }

    @Test
    void findByIdempotencyKey_returnsEmptyWhenMissing() {
        assertThat(authTxnRepository.findByIdempotencyKey("nope")).isEmpty();
    }

    @Test
    void idempotencyKey_uniqueIndex_rejectsDuplicateLiveRows() {
        CardAuthTransaction first = createAuthTxn("4111111111111111", "idem-dup");
        authTxnRepository.saveAndFlush(first);

        CardAuthTransaction second = createAuthTxn("4222222222222222", "idem-dup");

        assertThatThrownBy(() -> {
            authTxnRepository.save(second);
            authTxnRepository.flush();
        }).isInstanceOf(DataIntegrityViolationException.class);
    }

    // ---- OutboxRepository ----

    private Outbox createOutbox(String eventId, OutboxStatus status) {
        Outbox outbox = new Outbox();
        outbox.setAggregateType("Card");
        outbox.setAggregateId("1");
        outbox.setTopic("stats.payment.card.event");
        outbox.setPayload("{\"eventType\":\"card.created\"}");
        outbox.setStatus(status);
        outbox.setDomain("card");
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
