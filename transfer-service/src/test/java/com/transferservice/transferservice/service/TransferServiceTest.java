package com.transferservice.transferservice.service;

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
import com.transferservice.transferservice.dto.TransferRequest;
import com.transferservice.transferservice.entity.Outbox;
import com.transferservice.transferservice.entity.OutboxStatus;
import com.transferservice.transferservice.entity.Status;
import com.transferservice.transferservice.entity.Transfer;
import com.transferservice.transferservice.repository.OutboxRepository;
import com.transferservice.transferservice.repository.TransferRepository;

import io.opentelemetry.api.OpenTelemetry;

@ExtendWith(MockitoExtension.class)
class TransferServiceTest {

    @Mock
    private TransferRepository transferRepository;

    @Mock
    private OutboxRepository outboxRepository;

    private final com.transferservice.transferservice.dto.TransferMapper mapper =
            new com.transferservice.transferservice.dto.TransferMapperImpl();

    private final ObjectMapper objectMapper = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .build();

    private TransferService transferService;

    @BeforeEach
    void setUp() {
        transferService = new TransferService(
                transferRepository, outboxRepository, mapper, objectMapper, OpenTelemetry.noop());
    }

    private TransferRequest request(String from, String to, Integer amount, String idempotencyKey) {
        return new TransferRequest(from, to, amount, idempotencyKey);
    }

    private Transfer transfer(Long id, String from, String to, Integer amount) {
        Transfer t = new Transfer();
        t.setTransferId(id);
        t.setTransferFrom(from);
        t.setTransferTo(to);
        t.setTransferAmount(amount);
        t.setStatus(Status.PENDING);
        return t;
    }

    private void stubSaveAssignsId(long id) {
        when(transferRepository.save(any(Transfer.class))).thenAnswer(inv -> {
            Transfer t = inv.getArgument(0);
            t.setTransferId(id);
            return t;
        });
    }

    // ---- getAll / getById ----

    @Test
    void getAll_returnsAllFromRepository() {
        Transfer t1 = transfer(1L, "ACC-001", "ACC-002", 50000);
        Transfer t2 = transfer(2L, "ACC-002", "ACC-003", 75000);
        when(transferRepository.findAll()).thenReturn(List.of(t1, t2));

        assertThat(transferService.getAll()).containsExactly(t1, t2);
        verify(transferRepository).findAll();
    }

    @Test
    void getById_returnsTransferWhenFound() {
        Transfer existing = transfer(3L, "ACC-001", "ACC-002", 50000);
        when(transferRepository.findById(3L)).thenReturn(Optional.of(existing));

        assertThat(transferService.getById(3L)).isSameAs(existing);
    }

    @Test
    void getById_throwsWhenNotFound() {
        when(transferRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> transferService.getById(99L))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Transfer not found");
    }

    // ---- create: happy path + outbox write ----

    @Test
    void create_savesTransferAndWritesOutboxEvent() throws Exception {
        stubSaveAssignsId(7L);

        Transfer result = transferService.create(request("ACC-001", "ACC-002", 250000, "idem-1"));

        assertThat(result.getTransferId()).isEqualTo(7L);
        // mapper pins status to PENDING on create
        assertThat(result.getStatus()).isEqualTo(Status.PENDING);

        ArgumentCaptor<Outbox> outboxCaptor = ArgumentCaptor.forClass(Outbox.class);
        verify(outboxRepository).save(outboxCaptor.capture());
        Outbox outbox = outboxCaptor.getValue();

        assertThat(outbox.getAggregateType()).isEqualTo("Transfer");
        assertThat(outbox.getAggregateId()).isEqualTo("7");
        assertThat(outbox.getTopic()).isEqualTo("stats.payment.transfer.event");
        assertThat(outbox.getDomain()).isEqualTo("transfer");
        assertThat(outbox.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(outbox.getEventId()).isNotBlank();

        JsonNode envelope = objectMapper.readTree(outbox.getPayload());
        assertThat(envelope.get("eventType").asText()).isEqualTo("transfer.created");
        assertThat(envelope.get("domain").asText()).isEqualTo("transfer");
        assertThat(envelope.get("schemaVersion").asInt()).isEqualTo(1);
        assertThat(envelope.get("eventId").asText()).isEqualTo(outbox.getEventId());
        assertThat(envelope.get("occurredAt").asText()).isNotBlank();

        JsonNode payload = envelope.get("payload");
        assertThat(payload.get("transferId").asLong()).isEqualTo(7L);
        assertThat(payload.get("transferFrom").asText()).isEqualTo("ACC-001");
        assertThat(payload.get("transferTo").asText()).isEqualTo("ACC-002");
        assertThat(payload.get("amount").asInt()).isEqualTo(250000);
        assertThat(payload.get("status").asText()).isEqualTo("PENDING");
    }

    @Test
    void create_withNullAmount_defaultsPayloadAmountToZero() throws Exception {
        stubSaveAssignsId(9L);

        transferService.create(request("ACC-001", "ACC-002", null, null));

        ArgumentCaptor<Outbox> captor = ArgumentCaptor.forClass(Outbox.class);
        verify(outboxRepository).save(captor.capture());
        JsonNode payload = objectMapper.readTree(captor.getValue().getPayload()).get("payload");
        assertThat(payload.get("amount").asInt()).isZero();
    }

    // ---- create: idempotency guard ----

    @Test
    void create_withDuplicateIdempotencyKey_throwsAndSkipsSave() {
        when(transferRepository.findByIdempotencyKey("idem-1"))
                .thenReturn(Optional.of(new Transfer()));

        assertThatThrownBy(() -> transferService.create(request("ACC-001", "ACC-002", 50000, "idem-1")))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Duplicate idempotency key");

        verify(transferRepository, never()).save(any(Transfer.class));
        verify(outboxRepository, never()).save(any(Outbox.class));
    }

    @Test
    void create_withoutIdempotencyKey_skipsDuplicateLookupAndSaves() {
        stubSaveAssignsId(12L);

        Transfer result = transferService.create(request("ACC-001", "ACC-002", 50000, null));

        assertThat(result.getTransferId()).isEqualTo(12L);
        verify(transferRepository, never()).findByIdempotencyKey(any());
        verify(transferRepository).save(any(Transfer.class));
    }

    // ---- create: outbox failure must not fail the transfer ----

    @Test
    void create_withOutboxSaveFailure_stillPersistsTransfer() {
        stubSaveAssignsId(8L);
        when(outboxRepository.save(any(Outbox.class))).thenThrow(new RuntimeException("kafka down"));

        Transfer result = transferService.create(request("ACC-001", "ACC-002", 50000, null));

        assertThat(result.getTransferId()).isEqualTo(8L);
        verify(transferRepository).save(any(Transfer.class));
    }

    @Test
    void create_withOutboxSerializationFailure_stillPersistsTransfer() {
        // Bare ObjectMapper lacks JavaTimeModule -> EventEnvelope(Instant) serialization fails.
        // The service swallows it; the transfer itself must still be persisted.
        TransferService bareMapperService = new TransferService(
                transferRepository, outboxRepository, mapper, new ObjectMapper(), OpenTelemetry.noop());
        stubSaveAssignsId(13L);

        Transfer result = bareMapperService.create(request("ACC-001", "ACC-002", 50000, null));

        assertThat(result.getTransferId()).isEqualTo(13L);
        verify(transferRepository).save(any(Transfer.class));
        verify(outboxRepository, never()).save(any(Outbox.class));
    }

    // ---- update ----

    @Test
    void update_overwritesEditableFieldsAndSaves() {
        Transfer existing = transfer(1L, "ACC-001", "ACC-002", 50000);
        existing.setStatus(Status.SUCCESS);
        when(transferRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(transferRepository.save(any(Transfer.class))).thenAnswer(inv -> inv.getArgument(0));

        Transfer result = transferService.update(1L, request("ACC-009", "ACC-010", 99000, "idem-ignored"));

        assertThat(result.getTransferFrom()).isEqualTo("ACC-009");
        assertThat(result.getTransferTo()).isEqualTo("ACC-010");
        assertThat(result.getTransferAmount()).isEqualTo(99000);
        // quirk: update ignores idempotencyKey from the request and never touches status
        assertThat(result.getStatus()).isEqualTo(Status.SUCCESS);
        verify(transferRepository).save(existing);
    }

    @Test
    void update_throwsWhenTransferMissing() {
        when(transferRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> transferService.update(404L, request("ACC-009", "ACC-010", 99000, null)))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Transfer not found");

        verify(transferRepository, never()).save(any(Transfer.class));
    }

    // ---- delete ----

    @Test
    void delete_delegatesToRepository() {
        transferService.delete(5L);

        verify(transferRepository).deleteById(5L);
    }

    // ---- documented product gap ----

    @Test
    void create_doesNotPerformAnyBalanceOrAtomicTransferLogic() {
        // DOCUMENTED QUIRK (not a failing behavior): TransferService.create only records a
        // row + outbox event. There is no saldo check, no debit+credit pair, and no
        // atomicity between the two accounts — "transfer" is bookkeeping-only here.
        stubSaveAssignsId(20L);

        Transfer result = transferService.create(request("ACC-001", "ACC-002", 50000, null));

        verify(transferRepository).save(any(Transfer.class));
        verify(outboxRepository).save(any(Outbox.class));
        assertThat(result.getStatus()).isEqualTo(Status.PENDING);
        // strict mocks guarantee no other repository interaction happened
        verify(transferRepository, never()).findById(any());
    }
}
