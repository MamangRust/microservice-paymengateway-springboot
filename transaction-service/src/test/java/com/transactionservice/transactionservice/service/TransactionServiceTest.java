package com.transactionservice.transactionservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.transactionservice.transactionservice.dto.TransactionRequest;
import com.transactionservice.transactionservice.entity.Outbox;
import com.transactionservice.transactionservice.entity.OutboxStatus;
import com.transactionservice.transactionservice.entity.Status;
import com.transactionservice.transactionservice.entity.Transaction;
import com.transactionservice.transactionservice.repository.OutboxRepository;
import com.transactionservice.transactionservice.repository.TransactionRepository;

import io.opentelemetry.api.OpenTelemetry;

@ExtendWith(MockitoExtension.class)
class TransactionServiceTest {

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private OutboxRepository outboxRepository;

    // Real mapper (not a mock): create maps the request through it and writeOutbox
    // serializes the EventEnvelope with the same ObjectMapper the service holds.
    private final com.transactionservice.transactionservice.dto.TransactionMapper mapper =
            new com.transactionservice.transactionservice.dto.TransactionMapperImpl();

    // JavaTimeModule mirrors Spring Boot's auto-configured ObjectMapper in production —
    // EventEnvelope.occurredAt is an Instant which a bare ObjectMapper would fail on.
    private final ObjectMapper objectMapper = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .build();

    private TransactionService transactionService;

    @BeforeEach
    void setUp() {
        transactionService = new TransactionService(
                transactionRepository, outboxRepository, mapper, objectMapper, OpenTelemetry.noop());
    }

    private TransactionRequest request(String cardNumber, Integer amount, String paymentMethod, String idempotencyKey) {
        return new TransactionRequest(cardNumber, amount, paymentMethod, 1, idempotencyKey);
    }

    private Transaction transaction(Long id, String cardNumber, Integer amount) {
        Transaction txn = new Transaction();
        txn.setTransactionId(id);
        txn.setCardNumber(cardNumber);
        txn.setAmount(amount);
        txn.setPaymentMethod("QRIS");
        txn.setMerchantId(1);
        txn.setStatus(Status.PENDING);
        return txn;
    }

    private void stubSaveAssignsId(long id) {
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(inv -> {
            Transaction txn = inv.getArgument(0);
            txn.setTransactionId(id);
            return txn;
        });
    }

    // ---- getAll / getById ----

    @Test
    void getAll_returnsAllFromRepository() {
        Transaction t1 = transaction(1L, "4111111111111111", 50000);
        Transaction t2 = transaction(2L, "4222222222222222", 75000);
        when(transactionRepository.findAll()).thenReturn(List.of(t1, t2));

        assertThat(transactionService.getAll()).containsExactly(t1, t2);
        verify(transactionRepository).findAll();
    }

    @Test
    void getById_returnsTransactionWhenFound() {
        Transaction existing = transaction(3L, "4111111111111111", 50000);
        when(transactionRepository.findById(3L)).thenReturn(Optional.of(existing));

        assertThat(transactionService.getById(3L)).isSameAs(existing);
    }

    @Test
    void getById_throwsWhenNotFound() {
        when(transactionRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> transactionService.getById(99L))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Transaction not found");
    }

    // ---- create: happy path + outbox write ----

    @Test
    void create_savesTransactionAndWritesOutboxEvent() throws Exception {
        stubSaveAssignsId(7L);

        Transaction result = transactionService.create(request("4111111111111111", 111000, "QRIS", "idem-1"));

        assertThat(result.getTransactionId()).isEqualTo(7L);
        // mapper pins status to PENDING on create
        assertThat(result.getStatus()).isEqualTo(Status.PENDING);

        ArgumentCaptor<Outbox> outboxCaptor = ArgumentCaptor.forClass(Outbox.class);
        verify(outboxRepository).save(outboxCaptor.capture());
        Outbox outbox = outboxCaptor.getValue();

        assertThat(outbox.getAggregateType()).isEqualTo("Transaction");
        assertThat(outbox.getAggregateId()).isEqualTo("7");
        assertThat(outbox.getTopic()).isEqualTo("stats.payment.transaction.event");
        assertThat(outbox.getDomain()).isEqualTo("transaction");
        assertThat(outbox.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(outbox.getEventId()).isNotBlank();

        JsonNode envelope = objectMapper.readTree(outbox.getPayload());
        assertThat(envelope.get("eventType").asText()).isEqualTo("transaction.created");
        assertThat(envelope.get("domain").asText()).isEqualTo("transaction");
        assertThat(envelope.get("schemaVersion").asInt()).isEqualTo(1);
        assertThat(envelope.get("eventId").asText()).isEqualTo(outbox.getEventId());
        assertThat(envelope.get("occurredAt").asText()).isNotBlank();

        JsonNode payload = envelope.get("payload");
        assertThat(payload.get("transactionId").asLong()).isEqualTo(7L);
        assertThat(payload.get("cardNumber").asText()).isEqualTo("4111111111111111");
        assertThat(payload.get("amount").asInt()).isEqualTo(111000);
        assertThat(payload.get("paymentMethod").asText()).isEqualTo("QRIS");
        assertThat(payload.get("status").asText()).isEqualTo("PENDING");
    }

    @Test
    void create_withNullPaymentMethod_defaultsPayloadToEmptyString() throws Exception {
        stubSaveAssignsId(9L);

        transactionService.create(request("4111111111111111", 25000, null, null));

        ArgumentCaptor<Outbox> captor = ArgumentCaptor.forClass(Outbox.class);
        verify(outboxRepository).save(captor.capture());
        JsonNode payload = objectMapper.readTree(captor.getValue().getPayload()).get("payload");
        assertThat(payload.get("paymentMethod").asText()).isEmpty();
    }

    @Test
    void create_withNullAmount_defaultsPayloadAmountToZero() throws Exception {
        // entity quirk mirrored by the outbox writer: amount is nullable, writeOutbox
        // substitutes 0 instead of failing Map.of on a null value
        stubSaveAssignsId(10L);

        transactionService.create(new TransactionRequest("4111111111111111", null, "CASH", null, null));

        ArgumentCaptor<Outbox> captor = ArgumentCaptor.forClass(Outbox.class);
        verify(outboxRepository).save(captor.capture());
        JsonNode payload = objectMapper.readTree(captor.getValue().getPayload()).get("payload");
        assertThat(payload.get("amount").asInt()).isZero();
    }

    // ---- create: idempotency guard ----

    @Test
    void create_withDuplicateIdempotencyKey_throwsAndSkipsSave() {
        when(transactionRepository.findByIdempotencyKey("idem-1"))
                .thenReturn(Optional.of(new Transaction()));

        assertThatThrownBy(() -> transactionService.create(request("4111111111111111", 50000, "CASH", "idem-1")))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Duplicate idempotency key");

        verify(transactionRepository, never()).save(any(Transaction.class));
        verify(outboxRepository, never()).save(any(Outbox.class));
    }

    @Test
    void create_withoutIdempotencyKey_skipsDuplicateLookupAndSaves() {
        stubSaveAssignsId(12L);

        Transaction result = transactionService.create(request("4111111111111111", 50000, "CASH", null));

        assertThat(result.getTransactionId()).isEqualTo(12L);
        // Strict mocks double as an assertion: findByIdempotencyKey must never be consulted.
        verify(transactionRepository, never()).findByIdempotencyKey(any());
        verify(transactionRepository).save(any(Transaction.class));
    }

    // ---- create: outbox failure must not fail the transaction ----

    @Test
    void create_withOutboxSaveFailure_stillPersistsTransaction() {
        stubSaveAssignsId(8L);
        when(outboxRepository.save(any(Outbox.class))).thenThrow(new RuntimeException("kafka down"));

        Transaction result = transactionService.create(request("4111111111111111", 50000, "CASH", null));

        assertThat(result.getTransactionId()).isEqualTo(8L);
        verify(transactionRepository).save(any(Transaction.class));
    }

    @Test
    void create_withOutboxSerializationFailure_stillPersistsTransaction() {
        // Bare ObjectMapper lacks JavaTimeModule -> EventEnvelope(Instant) serialization fails.
        // The service swallows it; the transaction itself must still be persisted.
        TransactionService bareMapperService = new TransactionService(
                transactionRepository, outboxRepository, mapper, new ObjectMapper(), OpenTelemetry.noop());
        stubSaveAssignsId(13L);

        Transaction result = bareMapperService.create(request("4111111111111111", 50000, "CASH", null));

        assertThat(result.getTransactionId()).isEqualTo(13L);
        verify(transactionRepository).save(any(Transaction.class));
        verify(outboxRepository, never()).save(any(Outbox.class));
    }

    // ---- update ----

    @Test
    void update_overwritesEditableFieldsAndSaves() {
        Transaction existing = transaction(1L, "4111111111111111", 50000);
        existing.setStatus(Status.SUCCESS);
        existing.setIdempotencyKey("idem-original");
        when(transactionRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(inv -> inv.getArgument(0));

        Transaction result = transactionService.update(1L,
                new TransactionRequest("4222222222222222", 99000, "CASH", 2, "idem-ignored"));

        assertThat(result.getCardNumber()).isEqualTo("4222222222222222");
        assertThat(result.getAmount()).isEqualTo(99000);
        assertThat(result.getPaymentMethod()).isEqualTo("CASH");
        assertThat(result.getMerchantId()).isEqualTo(2);
        // quirk: update ignores idempotencyKey from the request and never touches status
        assertThat(result.getIdempotencyKey()).isEqualTo("idem-original");
        assertThat(result.getStatus()).isEqualTo(Status.SUCCESS);
        verify(transactionRepository).save(existing);
    }

    @Test
    void update_throwsWhenTransactionMissing() {
        when(transactionRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> transactionService.update(404L,
                new TransactionRequest("4222222222222222", 99000, "CASH", 2, null)))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Transaction not found");

        verify(transactionRepository, never()).save(any(Transaction.class));
    }

    // ---- delete ----

    @Test
    void delete_delegatesToRepository() {
        transactionService.delete(5L);

        verify(transactionRepository).deleteById(5L);
    }
}
