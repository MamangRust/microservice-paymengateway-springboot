package com.withdrawservice.withdrawservice.service;

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
import com.withdrawservice.withdrawservice.dto.WithdrawRequest;
import com.withdrawservice.withdrawservice.entity.Outbox;
import com.withdrawservice.withdrawservice.entity.OutboxStatus;
import com.withdrawservice.withdrawservice.entity.Status;
import com.withdrawservice.withdrawservice.entity.Withdraw;
import com.withdrawservice.withdrawservice.repository.OutboxRepository;
import com.withdrawservice.withdrawservice.repository.WithdrawRepository;

import io.opentelemetry.api.OpenTelemetry;

@ExtendWith(MockitoExtension.class)
class WithdrawServiceTest {

    @Mock
    private WithdrawRepository withdrawRepository;

    @Mock
    private OutboxRepository outboxRepository;

    private final com.withdrawservice.withdrawservice.dto.WithdrawMapper mapper =
            new com.withdrawservice.withdrawservice.dto.WithdrawMapperImpl();

    private final ObjectMapper objectMapper = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .build();

    private WithdrawService withdrawService;

    @BeforeEach
    void setUp() {
        withdrawService = new WithdrawService(
                withdrawRepository, outboxRepository, mapper, objectMapper, OpenTelemetry.noop());
    }

    private WithdrawRequest request(String cardNumber, Integer amount, String idempotencyKey) {
        return new WithdrawRequest(cardNumber, amount, idempotencyKey);
    }

    private Withdraw withdraw(Long id, String cardNumber, Integer amount) {
        Withdraw w = new Withdraw();
        w.setWithdrawId(id);
        w.setCardNumber(cardNumber);
        w.setWithdrawAmount(amount);
        w.setStatus(Status.PENDING);
        return w;
    }

    private void stubSaveAssignsId(long id) {
        when(withdrawRepository.save(any(Withdraw.class))).thenAnswer(inv -> {
            Withdraw w = inv.getArgument(0);
            w.setWithdrawId(id);
            return w;
        });
    }

    // ---- getAll / getById ----

    @Test
    void getAll_returnsAllFromRepository() {
        Withdraw w1 = withdraw(1L, "4111111111111111", 50000);
        Withdraw w2 = withdraw(2L, "4222222222222222", 75000);
        when(withdrawRepository.findAll()).thenReturn(List.of(w1, w2));

        assertThat(withdrawService.getAll()).containsExactly(w1, w2);
        verify(withdrawRepository).findAll();
    }

    @Test
    void getById_returnsWithdrawWhenFound() {
        Withdraw existing = withdraw(3L, "4111111111111111", 50000);
        when(withdrawRepository.findById(3L)).thenReturn(Optional.of(existing));

        assertThat(withdrawService.getById(3L)).isSameAs(existing);
    }

    @Test
    void getById_throwsWhenNotFound() {
        when(withdrawRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> withdrawService.getById(99L))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Withdraw not found");
    }

    // ---- create: happy path + outbox write ----

    @Test
    void create_savesWithdrawAndWritesOutboxEvent() throws Exception {
        stubSaveAssignsId(7L);

        Withdraw result = withdrawService.create(request("4111111111111111", 250000, "idem-1"));

        assertThat(result.getWithdrawId()).isEqualTo(7L);
        // mapper pins status to PENDING on create
        assertThat(result.getStatus()).isEqualTo(Status.PENDING);

        ArgumentCaptor<Outbox> outboxCaptor = ArgumentCaptor.forClass(Outbox.class);
        verify(outboxRepository).save(outboxCaptor.capture());
        Outbox outbox = outboxCaptor.getValue();

        assertThat(outbox.getAggregateType()).isEqualTo("Withdraw");
        assertThat(outbox.getAggregateId()).isEqualTo("7");
        assertThat(outbox.getTopic()).isEqualTo("stats.payment.withdraw.event");
        assertThat(outbox.getDomain()).isEqualTo("withdraw");
        assertThat(outbox.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(outbox.getEventId()).isNotBlank();

        JsonNode envelope = objectMapper.readTree(outbox.getPayload());
        assertThat(envelope.get("eventType").asText()).isEqualTo("withdraw.created");
        assertThat(envelope.get("domain").asText()).isEqualTo("withdraw");
        assertThat(envelope.get("schemaVersion").asInt()).isEqualTo(1);
        assertThat(envelope.get("eventId").asText()).isEqualTo(outbox.getEventId());
        assertThat(envelope.get("occurredAt").asText()).isNotBlank();

        JsonNode payload = envelope.get("payload");
        assertThat(payload.get("withdrawId").asLong()).isEqualTo(7L);
        assertThat(payload.get("cardNumber").asText()).isEqualTo("4111111111111111");
        assertThat(payload.get("amount").asInt()).isEqualTo(250000);
        assertThat(payload.get("status").asText()).isEqualTo("PENDING");
    }

    @Test
    void create_withNullAmount_defaultsPayloadAmountToZero() throws Exception {
        stubSaveAssignsId(9L);

        withdrawService.create(request("4111111111111111", null, null));

        ArgumentCaptor<Outbox> captor = ArgumentCaptor.forClass(Outbox.class);
        verify(outboxRepository).save(captor.capture());
        JsonNode payload = objectMapper.readTree(captor.getValue().getPayload()).get("payload");
        assertThat(payload.get("amount").asInt()).isZero();
    }

    // ---- create: idempotency guard ----

    @Test
    void create_withDuplicateIdempotencyKey_throwsAndSkipsSave() {
        when(withdrawRepository.findByIdempotencyKey("idem-1"))
                .thenReturn(Optional.of(new Withdraw()));

        assertThatThrownBy(() -> withdrawService.create(request("4111111111111111", 50000, "idem-1")))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Duplicate idempotency key");

        verify(withdrawRepository, never()).save(any(Withdraw.class));
        verify(outboxRepository, never()).save(any(Outbox.class));
    }

    @Test
    void create_withoutIdempotencyKey_skipsDuplicateLookupAndSaves() {
        stubSaveAssignsId(12L);

        Withdraw result = withdrawService.create(request("4111111111111111", 50000, null));

        assertThat(result.getWithdrawId()).isEqualTo(12L);
        verify(withdrawRepository, never()).findByIdempotencyKey(any());
        verify(withdrawRepository).save(any(Withdraw.class));
    }

    // ---- create: outbox failure must not fail the withdraw ----

    @Test
    void create_withOutboxSaveFailure_stillPersistsWithdraw() {
        stubSaveAssignsId(8L);
        when(outboxRepository.save(any(Outbox.class))).thenThrow(new RuntimeException("kafka down"));

        Withdraw result = withdrawService.create(request("4111111111111111", 50000, null));

        assertThat(result.getWithdrawId()).isEqualTo(8L);
        verify(withdrawRepository).save(any(Withdraw.class));
    }

    @Test
    void create_withOutboxSerializationFailure_stillPersistsWithdraw() {
        // Bare ObjectMapper lacks JavaTimeModule -> EventEnvelope(Instant) serialization fails.
        // The service swallows it; the withdraw itself must still be persisted.
        WithdrawService bareMapperService = new WithdrawService(
                withdrawRepository, outboxRepository, mapper, new ObjectMapper(), OpenTelemetry.noop());
        stubSaveAssignsId(13L);

        Withdraw result = bareMapperService.create(request("4111111111111111", 50000, null));

        assertThat(result.getWithdrawId()).isEqualTo(13L);
        verify(withdrawRepository).save(any(Withdraw.class));
        verify(outboxRepository, never()).save(any(Outbox.class));
    }

    // ---- update ----

    @Test
    void update_overwritesEditableFieldsAndSaves() {
        Withdraw existing = withdraw(1L, "4111111111111111", 50000);
        existing.setStatus(Status.SUCCESS);
        when(withdrawRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(withdrawRepository.save(any(Withdraw.class))).thenAnswer(inv -> inv.getArgument(0));

        Withdraw result = withdrawService.update(1L, request("4222222222222222", 99000, "idem-ignored"));

        assertThat(result.getCardNumber()).isEqualTo("4222222222222222");
        assertThat(result.getWithdrawAmount()).isEqualTo(99000);
        // quirk: update ignores idempotencyKey from the request and never touches status
        assertThat(result.getStatus()).isEqualTo(Status.SUCCESS);
        verify(withdrawRepository).save(existing);
    }

    @Test
    void update_throwsWhenWithdrawMissing() {
        when(withdrawRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> withdrawService.update(404L, request("4222222222222222", 99000, null)))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Withdraw not found");

        verify(withdrawRepository, never()).save(any(Withdraw.class));
    }

    // ---- delete ----

    @Test
    void delete_delegatesToRepository() {
        withdrawService.delete(5L);

        verify(withdrawRepository).deleteById(5L);
    }

    // ---- documented product gap ----

    @Test
    void create_acceptsAnyAmountBecauseNoLimitIsEnforced() {
        // DOCUMENTED QUIRK (not a failing behavior): there is no withdraw limit check —
        // zero and negative amounts are accepted and only produce an outbox event.
        stubSaveAssignsId(21L);

        Withdraw result = withdrawService.create(request("4111111111111111", -1, null));

        assertThat(result.getWithdrawId()).isEqualTo(21L);
        verify(withdrawRepository).save(any(Withdraw.class));
        verify(outboxRepository).save(any(Outbox.class));
    }
}
