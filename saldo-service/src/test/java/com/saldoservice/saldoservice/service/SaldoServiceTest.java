package com.saldoservice.saldoservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
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
import org.springframework.dao.EmptyResultDataAccessException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.saldoservice.saldoservice.dto.SaldoMapper;
import com.saldoservice.saldoservice.dto.SaldoMapperImpl;
import com.saldoservice.saldoservice.dto.SaldoRequest;
import com.saldoservice.saldoservice.entity.Outbox;
import com.saldoservice.saldoservice.entity.OutboxStatus;
import com.saldoservice.saldoservice.entity.Saldo;
import com.saldoservice.saldoservice.entity.SaldoMutationOperation;
import com.saldoservice.saldoservice.repository.OutboxRepository;
import com.saldoservice.saldoservice.repository.SaldoMutationOperationRepository;
import com.saldoservice.saldoservice.repository.SaldoRepository;

import io.opentelemetry.api.OpenTelemetry;

@ExtendWith(MockitoExtension.class)
class SaldoServiceTest {

    @Mock
    private SaldoRepository saldoRepository;

    @Mock
    private SaldoMutationOperationRepository mutationRepository;

    @Mock
    private OutboxRepository outboxRepository;

    private SaldoService saldoService;

    private final SaldoMapper saldoMapper = new SaldoMapperImpl();

    private final ObjectMapper objectMapper = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .build();

    @BeforeEach
    void setUp() {
        saldoService = new SaldoService(saldoRepository, mutationRepository, outboxRepository,
                saldoMapper, objectMapper, OpenTelemetry.noop());
    }

    private Saldo createSaldo(Long saldoId, String cardNumber, Integer totalBalance) {
        Saldo saldo = new Saldo();
        saldo.setSaldoId(saldoId);
        saldo.setCardNumber(cardNumber);
        saldo.setTotalBalance(totalBalance);
        return saldo;
    }

    // ---- read passthroughs ----

    @Test
    void getAll_returnsAllFromRepository() {
        Saldo s1 = createSaldo(1L, "C-001", 100);
        Saldo s2 = createSaldo(2L, "C-002", 200);
        when(saldoRepository.findAll()).thenReturn(List.of(s1, s2));

        assertThat(saldoService.getAll()).containsExactly(s1, s2);
        verify(saldoRepository).findAll();
    }

    @Test
    void getById_returnsSaldoWhenFound() {
        Saldo existing = createSaldo(1L, "C-001", 100);
        when(saldoRepository.findById(1L)).thenReturn(Optional.of(existing));

        assertThat(saldoService.getById(1L)).isSameAs(existing);
    }

    @Test
    void getById_throwsWhenNotFound() {
        when(saldoRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> saldoService.getById(999L))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Saldo not found");
    }

    @Test
    void getByCardNumber_returnsSaldoWhenFound() {
        Saldo existing = createSaldo(1L, "C-001", 100);
        when(saldoRepository.findByCardNumber("C-001")).thenReturn(Optional.of(existing));

        assertThat(saldoService.getByCardNumber("C-001")).isSameAs(existing);
    }

    @Test
    void getByCardNumber_throwsSameMessageWhenNotFound() {
        when(saldoRepository.findByCardNumber("nope")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> saldoService.getByCardNumber("nope"))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Saldo not found");
    }

    // ---- create: save + outbox write ----

    @Test
    void create_savesMappedEntityAndWritesOutboxEvent() throws Exception {
        SaldoRequest request = new SaldoRequest("C-100", 250);
        when(saldoRepository.save(any(Saldo.class))).thenAnswer(inv -> {
            Saldo saldo = inv.getArgument(0);
            saldo.setSaldoId(7L);
            return saldo;
        });

        Saldo result = saldoService.create(request);

        assertThat(result.getSaldoId()).isEqualTo(7L);

        ArgumentCaptor<Saldo> saldoCaptor = ArgumentCaptor.forClass(Saldo.class);
        verify(saldoRepository).save(saldoCaptor.capture());
        assertThat(saldoCaptor.getValue().getCardNumber()).isEqualTo("C-100");
        assertThat(saldoCaptor.getValue().getTotalBalance()).isEqualTo(250);

        ArgumentCaptor<Outbox> outboxCaptor = ArgumentCaptor.forClass(Outbox.class);
        verify(outboxRepository).save(outboxCaptor.capture());
        Outbox outbox = outboxCaptor.getValue();

        assertThat(outbox.getAggregateType()).isEqualTo("Saldo");
        assertThat(outbox.getAggregateId()).isEqualTo("7");
        assertThat(outbox.getTopic()).isEqualTo("stats.payment.saldo.event");
        assertThat(outbox.getDomain()).isEqualTo("saldo");
        assertThat(outbox.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(outbox.getEventId()).isNotBlank();

        JsonNode envelope = objectMapper.readTree(outbox.getPayload());
        assertThat(envelope.get("eventType").asText()).isEqualTo("saldo.created");
        assertThat(envelope.get("domain").asText()).isEqualTo("saldo");
        assertThat(envelope.get("schemaVersion").asInt()).isEqualTo(1);
        assertThat(envelope.get("eventId").asText()).isEqualTo(outbox.getEventId());

        JsonNode payload = envelope.get("payload");
        assertThat(payload.get("saldoId").asLong()).isEqualTo(7L);
        assertThat(payload.get("cardNumber").asText()).isEqualTo("C-100");
        assertThat(payload.get("totalBalance").asInt()).isEqualTo(250);
    }

    @Test
    void create_withNullFields_writesDefaultsInOutboxPayload() throws Exception {
        // SaldoRequest is a record — service performs no @Valid check of its own;
        // writeOutbox null-guards cardNumber/totalBalance into ""/0.
        SaldoRequest request = new SaldoRequest(null, null);
        when(saldoRepository.save(any(Saldo.class))).thenAnswer(inv -> {
            Saldo saldo = inv.getArgument(0);
            saldo.setSaldoId(8L);
            return saldo;
        });

        saldoService.create(request);

        ArgumentCaptor<Outbox> captor = ArgumentCaptor.forClass(Outbox.class);
        verify(outboxRepository).save(captor.capture());
        JsonNode payload = objectMapper.readTree(captor.getValue().getPayload()).get("payload");
        assertThat(payload.get("cardNumber").asText()).isEmpty();
        assertThat(payload.get("totalBalance").asInt()).isZero();
    }

    @Test
    void create_outboxFailureIsSwallowed() {
        when(saldoRepository.save(any(Saldo.class))).thenAnswer(inv -> {
            Saldo saldo = inv.getArgument(0);
            saldo.setSaldoId(9L);
            return saldo;
        });
        when(outboxRepository.save(any(Outbox.class))).thenThrow(new RuntimeException("kafka down"));

        Saldo result = saldoService.create(new SaldoRequest("C-100", 250));

        assertThat(result.getSaldoId()).isEqualTo(9L);
        verify(saldoRepository).save(any(Saldo.class));
    }

    @Test
    void create_outboxSerializationFailureIsSwallowed() {
        // Bare ObjectMapper lacks JavaTimeModule -> EventEnvelope(Instant) serialization
        // fails inside writeOutbox; the exception is swallowed and the saldo is still
        // returned, but the outbox row is never persisted.
        SaldoService bareMapperService = new SaldoService(saldoRepository, mutationRepository,
                outboxRepository, saldoMapper, new ObjectMapper(), OpenTelemetry.noop());
        when(saldoRepository.save(any(Saldo.class))).thenAnswer(inv -> {
            Saldo saldo = inv.getArgument(0);
            saldo.setSaldoId(10L);
            return saldo;
        });

        Saldo result = bareMapperService.create(new SaldoRequest("C-100", 250));

        assertThat(result.getSaldoId()).isEqualTo(10L);
        verify(saldoRepository).save(any(Saldo.class));
        verify(outboxRepository, never()).save(any(Outbox.class));
    }

    // ---- mutate: idempotent balance mutation (check-then-act on operation_key) ----

    @Test
    void mutate_credit_addsDeltaAndRecordsSuccessLedgerRow() {
        Saldo saldo = createSaldo(1L, "C-001", 100);
        when(mutationRepository.findByOperationKey("op-1")).thenReturn(Optional.empty());
        when(saldoRepository.findByCardNumber("C-001")).thenReturn(Optional.of(saldo));
        when(saldoRepository.save(any(Saldo.class))).thenAnswer(inv -> inv.getArgument(0));
        when(mutationRepository.save(any(SaldoMutationOperation.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        SaldoMutationOperation result = saldoService.mutate("op-1", "C-001", 50, 0);

        // saldo row updated
        assertThat(saldo.getTotalBalance()).isEqualTo(150);
        verify(saldoRepository).save(saldo);

        // ledger row recorded with the request fields and SUCCESS outcome
        ArgumentCaptor<SaldoMutationOperation> opCaptor =
                ArgumentCaptor.forClass(SaldoMutationOperation.class);
        verify(mutationRepository).save(opCaptor.capture());
        SaldoMutationOperation op = opCaptor.getValue();
        assertThat(op.getOperationKey()).isEqualTo("op-1");
        assertThat(op.getCardNumber()).isEqualTo("C-001");
        assertThat(op.getRequestedDelta()).isEqualTo(50);
        assertThat(op.getMinimumBalance()).isZero();
        assertThat(op.getResultStatus()).isEqualTo("SUCCESS");
        assertThat(op.getResultBalance()).isEqualTo(150);
        assertThat(op.getFailureReason()).isNull();
        assertThat(result).isSameAs(op);
    }

    @Test
    void mutate_debit_succeedsWhenAboveMinimum() {
        Saldo saldo = createSaldo(1L, "C-001", 100);
        when(mutationRepository.findByOperationKey("op-2")).thenReturn(Optional.empty());
        when(saldoRepository.findByCardNumber("C-001")).thenReturn(Optional.of(saldo));
        when(saldoRepository.save(any(Saldo.class))).thenAnswer(inv -> inv.getArgument(0));
        when(mutationRepository.save(any(SaldoMutationOperation.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        SaldoMutationOperation result = saldoService.mutate("op-2", "C-001", -30, 50);

        assertThat(saldo.getTotalBalance()).isEqualTo(70);
        assertThat(result.getResultStatus()).isEqualTo("SUCCESS");
        assertThat(result.getResultBalance()).isEqualTo(70);
    }

    @Test
    void mutate_debit_atExactMinimumBalance_succeeds() {
        // boundary: newBalance == minimumBalance is allowed (only strictly-less rejects)
        Saldo saldo = createSaldo(1L, "C-001", 100);
        when(mutationRepository.findByOperationKey("op-3")).thenReturn(Optional.empty());
        when(saldoRepository.findByCardNumber("C-001")).thenReturn(Optional.of(saldo));
        when(saldoRepository.save(any(Saldo.class))).thenAnswer(inv -> inv.getArgument(0));
        when(mutationRepository.save(any(SaldoMutationOperation.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        SaldoMutationOperation result = saldoService.mutate("op-3", "C-001", -50, 50);

        assertThat(saldo.getTotalBalance()).isEqualTo(50);
        assertThat(result.getResultBalance()).isEqualTo(50);
        assertThat(result.getResultStatus()).isEqualTo("SUCCESS");
    }

    @Test
    void mutate_debit_belowMinimum_throwsInsufficientBalance() {
        // 100 + (-60) = 40 < 50 -> rejected
        Saldo saldo = createSaldo(1L, "C-001", 100);
        when(mutationRepository.findByOperationKey("op-4")).thenReturn(Optional.empty());
        when(saldoRepository.findByCardNumber("C-001")).thenReturn(Optional.of(saldo));

        assertThatThrownBy(() -> saldoService.mutate("op-4", "C-001", -60, 50))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Insufficient balance");

        // neither the saldo nor the ledger row may be touched on failure
        verify(saldoRepository, never()).save(any(Saldo.class));
        verify(mutationRepository, never()).save(any(SaldoMutationOperation.class));
    }

    @Test
    void mutate_duplicateOperationKey_throwsAndSkipsAllWrites() {
        when(mutationRepository.findByOperationKey("op-dup"))
                .thenReturn(Optional.of(new SaldoMutationOperation()));

        assertThatThrownBy(() -> saldoService.mutate("op-dup", "C-001", 10, 0))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Duplicate operation key");

        // duplicate check happens first: saldo never even looked up, nothing written
        verify(saldoRepository, never()).findByCardNumber(any());
        verify(saldoRepository, never()).save(any(Saldo.class));
        verify(mutationRepository, never()).save(any(SaldoMutationOperation.class));
    }

    @Test
    void mutate_saldoMissing_throwsAndWritesNothing() {
        when(mutationRepository.findByOperationKey("op-5")).thenReturn(Optional.empty());
        when(saldoRepository.findByCardNumber("unknown")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> saldoService.mutate("op-5", "unknown", 10, 0))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Saldo not found");

        verify(saldoRepository, never()).save(any(Saldo.class));
        verify(mutationRepository, never()).save(any(SaldoMutationOperation.class));
    }

    // ---- update: overwrites cardNumber/totalBalance directly, no guard ----

    @Test
    void update_overwritesCardNumberAndTotalBalanceDirectly() {
        // documents actual behavior: no validation, no balance guard —
        // cardNumber is rewritten to another card's number and balance set arbitrarily
        Saldo existing = createSaldo(1L, "C-001", 100);
        when(saldoRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(saldoRepository.save(any(Saldo.class))).thenAnswer(inv -> inv.getArgument(0));

        Saldo result = saldoService.update(1L, new SaldoRequest("C-999", 999999));

        assertThat(result.getSaldoId()).isEqualTo(1L);
        assertThat(result.getCardNumber()).isEqualTo("C-999");
        assertThat(result.getTotalBalance()).isEqualTo(999999);
        verify(saldoRepository).save(existing);
    }

    @Test
    void update_throwsWhenNotFound() {
        when(saldoRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> saldoService.update(999L, new SaldoRequest("C-001", 1)))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Saldo not found");

        verify(saldoRepository, never()).save(any(Saldo.class));
    }

    // ---- delete: derived deleteById, no existence check ----

    @Test
    void delete_delegatesToDeleteById() {
        saldoService.delete(1L);

        verify(saldoRepository).deleteById(1L);
    }

    @Test
    void delete_missingId_propagatesEmptyResultDataAccessException() {
        // documents actual behavior: no existence check — Spring Data's
        // EmptyResultDataAccessException propagates to the caller
        doThrow(new EmptyResultDataAccessException(1)).when(saldoRepository).deleteById(999L);

        assertThatThrownBy(() -> saldoService.delete(999L))
                .isInstanceOf(EmptyResultDataAccessException.class);
    }
}
