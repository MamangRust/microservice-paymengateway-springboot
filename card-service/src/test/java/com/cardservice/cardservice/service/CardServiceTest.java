package com.cardservice.cardservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.cardservice.cardservice.dto.CardMapper;
import com.cardservice.cardservice.dto.CardMapperImpl;
import com.cardservice.cardservice.dto.CardRequest;
import com.cardservice.cardservice.entity.AuthStatus;
import com.cardservice.cardservice.entity.Card;
import com.cardservice.cardservice.entity.CardAuthTransaction;
import com.cardservice.cardservice.entity.CardStatus;
import com.cardservice.cardservice.entity.Outbox;
import com.cardservice.cardservice.entity.OutboxStatus;
import com.cardservice.cardservice.repository.CardAuthTransactionRepository;
import com.cardservice.cardservice.repository.CardRepository;
import com.cardservice.cardservice.repository.OutboxRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import io.opentelemetry.api.OpenTelemetry;

@ExtendWith(MockitoExtension.class)
class CardServiceTest {

    @Mock
    private CardRepository cardRepository;

    @Mock
    private CardAuthTransactionRepository authTxnRepository;

    @Mock
    private OutboxRepository outboxRepository;

    private CardService cardService;

    // Real MapStruct mapper (not a mock): same impl Spring generates via APT.
    private final CardMapper cardMapper = new CardMapperImpl();

    // JavaTimeModule mirrors Spring Boot's auto-configured ObjectMapper —
    // EventEnvelope.occurredAt is an Instant which a bare ObjectMapper fails on.
    private final ObjectMapper objectMapper = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .build();

    @BeforeEach
    void setUp() {
        cardService = new CardService(cardRepository, authTxnRepository, outboxRepository,
                cardMapper, objectMapper, OpenTelemetry.noop());
    }

    private Card createCard(Long cardId, Integer userId, String cardNumber) {
        Card card = new Card();
        card.setCardId(cardId);
        card.setUserId(userId);
        card.setCardNumber(cardNumber);
        card.setCardType("CREDIT");
        card.setExpireDate(LocalDate.of(2027, 12, 31));
        card.setCvv("123");
        card.setCardProvider("VISA");
        card.setCreditLimit(new BigDecimal("5000.00"));
        card.setPoints(new BigDecimal("120.50"));
        return card;
    }

    private CardRequest createRequest(Integer userId, String cardNumber) {
        return new CardRequest(userId, cardNumber, "CREDIT", LocalDate.of(2027, 12, 31),
                "123", "VISA", new BigDecimal("5000.00"), new BigDecimal("10.00"));
    }

    // ---- read passthroughs ----

    @Test
    void getAllCards_returnsAllFromRepository() {
        Card c1 = createCard(1L, 1, "4111111111111111");
        Card c2 = createCard(2L, 2, "4222222222222222");
        when(cardRepository.findAll()).thenReturn(List.of(c1, c2));

        List<Card> result = cardService.getAllCards();

        assertThat(result).containsExactly(c1, c2);
        verify(cardRepository).findAll();
    }

    @Test
    void getCardById_returnsCardWhenFound() {
        Card existing = createCard(1L, 1, "4111111111111111");
        when(cardRepository.findById(1L)).thenReturn(Optional.of(existing));

        assertThat(cardService.getCardById(1L)).isSameAs(existing);
    }

    @Test
    void getCardById_throwsWhenNotFound() {
        when(cardRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> cardService.getCardById(999L))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Card not found");
    }

    @Test
    void getByCardNumber_returnsCardWhenFound() {
        Card existing = createCard(1L, 1, "4111111111111111");
        when(cardRepository.findByCardNumber("4111111111111111")).thenReturn(Optional.of(existing));

        assertThat(cardService.getByCardNumber("4111111111111111")).isSameAs(existing);
    }

    @Test
    void getByCardNumber_throwsSameMessageWhenNotFound() {
        when(cardRepository.findByCardNumber("nope")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> cardService.getByCardNumber("nope"))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Card not found");
    }

    @Test
    void getByUserId_returnsFromRepository() {
        Card mine = createCard(1L, 7, "4111111111111111");
        when(cardRepository.findByUserId(7)).thenReturn(List.of(mine));

        List<Card> result = cardService.getByUserId(7);

        assertThat(result).containsExactly(mine);
        verify(cardRepository).findByUserId(7);
    }

    @Test
    void getByUserId_returnsEmptyWhenNoMatch() {
        when(cardRepository.findByUserId(42)).thenReturn(List.of());

        assertThat(cardService.getByUserId(42)).isEmpty();
    }

    // ---- createCard: save + outbox write ----

    @Test
    void createCard_mapsRequestSavesAndWritesOutboxEvent() throws Exception {
        CardRequest request = createRequest(1, "4111111111111111");
        when(cardRepository.save(any(Card.class))).thenAnswer(inv -> {
            Card card = inv.getArgument(0);
            card.setCardId(5L);
            return card;
        });

        Card result = cardService.createCard(request);

        assertThat(result.getCardId()).isEqualTo(5L);

        ArgumentCaptor<Card> cardCaptor = ArgumentCaptor.forClass(Card.class);
        verify(cardRepository).save(cardCaptor.capture());
        assertThat(cardCaptor.getValue().getCardNumber()).isEqualTo("4111111111111111");
        assertThat(cardCaptor.getValue().getUserId()).isEqualTo(1);
        assertThat(cardCaptor.getValue().getCvv()).isEqualTo("123");

        ArgumentCaptor<Outbox> outboxCaptor = ArgumentCaptor.forClass(Outbox.class);
        verify(outboxRepository).save(outboxCaptor.capture());
        Outbox outbox = outboxCaptor.getValue();

        assertThat(outbox.getAggregateType()).isEqualTo("Card");
        assertThat(outbox.getAggregateId()).isEqualTo("5");
        assertThat(outbox.getTopic()).isEqualTo("stats.payment.card.event");
        assertThat(outbox.getDomain()).isEqualTo("card");
        assertThat(outbox.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(outbox.getEventId()).isNotBlank();

        JsonNode envelope = objectMapper.readTree(outbox.getPayload());
        assertThat(envelope.get("eventType").asText()).isEqualTo("card.created");
        assertThat(envelope.get("domain").asText()).isEqualTo("card");
        assertThat(envelope.get("schemaVersion").asInt()).isEqualTo(1);
        assertThat(envelope.get("eventId").asText()).isEqualTo(outbox.getEventId());

        JsonNode payload = envelope.get("payload");
        assertThat(payload.get("cardId").asLong()).isEqualTo(5L);
        assertThat(payload.get("cardNumber").asText()).isEqualTo("4111111111111111");
        assertThat(payload.get("userId").asInt()).isEqualTo(1);
        assertThat(payload.get("status").asText()).isEqualTo("ACTIVE");
    }

    @Test
    void createCard_outboxFailureIsSwallowed() {
        when(cardRepository.save(any(Card.class))).thenAnswer(inv -> {
            Card card = inv.getArgument(0);
            card.setCardId(6L);
            return card;
        });
        when(outboxRepository.save(any(Outbox.class))).thenThrow(new RuntimeException("kafka down"));

        Card result = cardService.createCard(createRequest(1, "4111111111111111"));

        assertThat(result.getCardId()).isEqualTo(6L);
        verify(cardRepository).save(any(Card.class));
    }

    @Test
    void createCard_outboxSerializationFailureIsSwallowed() {
        // Bare ObjectMapper lacks JavaTimeModule -> EventEnvelope(Instant) serialization
        // fails inside writeOutbox; the exception is swallowed and the card is still
        // returned, but the outbox row is never persisted.
        CardService bareMapperService = new CardService(cardRepository, authTxnRepository,
                outboxRepository, cardMapper, new ObjectMapper(), OpenTelemetry.noop());
        when(cardRepository.save(any(Card.class))).thenAnswer(inv -> {
            Card card = inv.getArgument(0);
            card.setCardId(7L);
            return card;
        });

        Card result = bareMapperService.createCard(createRequest(1, "4111111111111111"));

        assertThat(result.getCardId()).isEqualTo(7L);
        verify(cardRepository).save(any(Card.class));
        verify(outboxRepository, never()).save(any(Outbox.class));
    }

    // ---- updateCard: only cardType/expireDate/cardProvider/creditLimit/points ----

    @Test
    void updateCard_updatesMutableFieldsOnly() {
        Card existing = createCard(1L, 1, "4111111111111111");
        existing.setStatus(CardStatus.SUSPENDED);
        when(cardRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(cardRepository.save(any(Card.class))).thenAnswer(inv -> inv.getArgument(0));

        // CardRequest is a record: cardNumber and cvv are always carried along,
        // but updateCard must not copy them onto the entity.
        CardRequest request = new CardRequest(9, "9999999999999999", "DEBIT",
                LocalDate.of(2029, 6, 30), "999", "MASTERCARD",
                new BigDecimal("8000.00"), new BigDecimal("55.00"));

        Card result = cardService.updateCard(1L, request);

        assertThat(result.getCardType()).isEqualTo("DEBIT");
        assertThat(result.getExpireDate()).isEqualTo(LocalDate.of(2029, 6, 30));
        assertThat(result.getCardProvider()).isEqualTo("MASTERCARD");
        assertThat(result.getCreditLimit()).isEqualByComparingTo("8000.00");
        assertThat(result.getPoints()).isEqualByComparingTo("55.00");

        // immutables: cardNumber and cvv untouched
        assertThat(result.getCardNumber()).isEqualTo("4111111111111111");
        assertThat(result.getCvv()).isEqualTo("123");
        // userId not part of the update either
        assertThat(result.getUserId()).isEqualTo(1);
        verify(cardRepository).save(existing);
    }

    @Test
    void updateCard_throwsWhenNotFound() {
        when(cardRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> cardService.updateCard(999L, createRequest(1, "4111111111111111")))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Card not found");

        verify(cardRepository, never()).save(any(Card.class));
    }

    // ---- deleteCard ----

    @Test
    void deleteCard_delegatesToRepository() {
        cardService.deleteCard(1L);

        verify(cardRepository).deleteById(1L);
    }

    // ---- authorize: idempotency guard, no decision logic ----

    @Test
    void authorize_savesTxnWhenIdempotencyKeyIsNew() {
        CardAuthTransaction txn = createAuthTxn("4111111111111111", "idem-1");
        when(authTxnRepository.findByIdempotencyKey("idem-1")).thenReturn(Optional.empty());
        when(authTxnRepository.save(txn)).thenReturn(txn);

        CardAuthTransaction result = cardService.authorize(txn);

        assertThat(result).isSameAs(txn);
        // actual behavior: no decision logic — status stays at entity default
        assertThat(result.getStatus()).isEqualTo(AuthStatus.PENDING);
        verify(authTxnRepository).save(txn);
    }

    @Test
    void authorize_duplicateIdempotencyKeyThrowsAndSkipsSave() {
        CardAuthTransaction txn = createAuthTxn("4111111111111111", "idem-dup");
        when(authTxnRepository.findByIdempotencyKey("idem-dup"))
                .thenReturn(Optional.of(new CardAuthTransaction()));

        assertThatThrownBy(() -> cardService.authorize(txn))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Duplicate idempotency key");

        verify(authTxnRepository, never()).save(any(CardAuthTransaction.class));
    }

    @Test
    void authorize_withoutIdempotencyKey_skipsDuplicateLookupAndSaves() {
        CardAuthTransaction txn = createAuthTxn("4111111111111111", null);
        when(authTxnRepository.save(txn)).thenReturn(txn);

        CardAuthTransaction result = cardService.authorize(txn);

        assertThat(result).isSameAs(txn);
        // Strict mocks double as an assertion: findByIdempotencyKey must never be consulted.
        verify(authTxnRepository).save(txn);
    }

    @Test
    void authorize_savedTxnKeepsEntityDefaultStatus() {
        // documents actual behavior: authorize performs no APPROVED/DECLINED transition
        CardAuthTransaction txn = createAuthTxn("4111111111111111", "idem-2");
        txn.setStatus(AuthStatus.DECLINED);
        when(authTxnRepository.findByIdempotencyKey("idem-2")).thenReturn(Optional.empty());
        when(authTxnRepository.save(txn)).thenReturn(txn);

        assertThat(cardService.authorize(txn).getStatus()).isEqualTo(AuthStatus.DECLINED);
    }

    // ---- getAuthTransactions ----

    @Test
    void getAuthTransactions_returnsFromRepository() {
        CardAuthTransaction txn = createAuthTxn("4111111111111111", "idem-3");
        when(authTxnRepository.findByCardNumber("4111111111111111")).thenReturn(List.of(txn));

        List<CardAuthTransaction> result = cardService.getAuthTransactions("4111111111111111");

        assertThat(result).containsExactly(txn);
        verify(authTxnRepository).findByCardNumber("4111111111111111");
    }

    @Test
    void getAuthTransactions_returnsEmptyWhenNoMatch() {
        when(authTxnRepository.findByCardNumber("nope")).thenReturn(List.of());

        assertThat(cardService.getAuthTransactions("nope")).isEmpty();
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
}
