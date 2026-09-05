package com.topupservice.topupservice.service;

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
import com.topupservice.topupservice.dto.TopupMapper;
import com.topupservice.topupservice.dto.TopupMapperImpl;
import com.topupservice.topupservice.dto.TopupRequest;
import com.topupservice.topupservice.entity.Outbox;
import com.topupservice.topupservice.entity.OutboxStatus;
import com.topupservice.topupservice.entity.Status;
import com.topupservice.topupservice.entity.Topup;
import com.topupservice.topupservice.repository.OutboxRepository;
import com.topupservice.topupservice.repository.TopupRepository;

import io.opentelemetry.api.OpenTelemetry;

@ExtendWith(MockitoExtension.class)
class TopupServiceTest {

    @Mock
    private TopupRepository topupRepository;

    @Mock
    private OutboxRepository outboxRepository;

    private TopupService topupService;

    private final TopupMapper topupMapper = new TopupMapperImpl();

    private final ObjectMapper objectMapper = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .build();

    @BeforeEach
    void setUp() {
        topupService = new TopupService(topupRepository, outboxRepository, topupMapper,
                objectMapper, OpenTelemetry.noop());
    }

    private Topup createTopup(Long topupId, String cardNumber, Integer amount) {
        Topup topup = new Topup();
        topup.setTopupId(topupId);
        topup.setCardNumber(cardNumber);
        topup.setTopupAmount(amount);
        topup.setTopupMethod("BANK_TRANSFER");
        topup.setIdempotencyKey("idem-" + topupId);
        return topup;
    }

    private TopupRequest createRequest(String cardNumber, Integer amount) {
        return new TopupRequest(cardNumber, amount, "BANK_TRANSFER", "idem-new");
    }

    // ---- read passthroughs ----

    @Test
    void getAll_returnsAllFromRepository() {
        Topup t1 = createTopup(1L, "C-001", 50000);
        Topup t2 = createTopup(2L, "C-002", 100000);
        when(topupRepository.findAll()).thenReturn(List.of(t1, t2));

        assertThat(topupService.getAll()).containsExactly(t1, t2);
        verify(topupRepository).findAll();
    }

    @Test
    void getById_returnsTopupWhenFound() {
        Topup existing = createTopup(1L, "C-001", 50000);
        when(topupRepository.findById(1L)).thenReturn(Optional.of(existing));

        assertThat(topupService.getById(1L)).isSameAs(existing);
    }

    @Test
    void getById_throwsWhenNotFound() {
        when(topupRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> topupService.getById(999L))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Topup not found");
    }

    // ---- create: idempotency guard + save + outbox write ----

    @Test
    void create_savesMappedEntityAndWritesOutboxEvent() throws Exception {
        TopupRequest request = createRequest("C-001", 50000);
        when(topupRepository.findByIdempotencyKey("idem-new")).thenReturn(Optional.empty());
        when(topupRepository.save(any(Topup.class))).thenAnswer(inv -> {
            Topup topup = inv.getArgument(0);
            topup.setTopupId(7L);
            return topup;
        });

        Topup result = topupService.create(request);

        assertThat(result.getTopupId()).isEqualTo(7L);

        ArgumentCaptor<Topup> topupCaptor = ArgumentCaptor.forClass(Topup.class);
        verify(topupRepository).save(topupCaptor.capture());
        Topup saved = topupCaptor.getValue();
        assertThat(saved.getCardNumber()).isEqualTo("C-001");
        assertThat(saved.getTopupAmount()).isEqualTo(50000);
        assertThat(saved.getTopupMethod()).isEqualTo("BANK_TRANSFER");
        // mapper sets status via constant; topupNo/topupTime left to the entity/lifecycle
        assertThat(saved.getStatus()).isEqualTo(Status.PENDING);
        assertThat(saved.getTopupNo()).isNotBlank();

        ArgumentCaptor<Outbox> outboxCaptor = ArgumentCaptor.forClass(Outbox.class);
        verify(outboxRepository).save(outboxCaptor.capture());
        Outbox outbox = outboxCaptor.getValue();

        assertThat(outbox.getAggregateType()).isEqualTo("Topup");
        assertThat(outbox.getAggregateId()).isEqualTo("7");
        assertThat(outbox.getTopic()).isEqualTo("stats.payment.topup.event");
        assertThat(outbox.getDomain()).isEqualTo("topup");
        assertThat(outbox.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(outbox.getEventId()).isNotBlank();

        JsonNode envelope = objectMapper.readTree(outbox.getPayload());
        assertThat(envelope.get("eventType").asText()).isEqualTo("topup.created");
        assertThat(envelope.get("domain").asText()).isEqualTo("topup");
        assertThat(envelope.get("schemaVersion").asInt()).isEqualTo(1);
        assertThat(envelope.get("eventId").asText()).isEqualTo(outbox.getEventId());

        JsonNode payload = envelope.get("payload");
        assertThat(payload.get("topupId").asLong()).isEqualTo(7L);
        assertThat(payload.get("cardNumber").asText()).isEqualTo("C-001");
        assertThat(payload.get("amount").asInt()).isEqualTo(50000);
        assertThat(payload.get("status").asText()).isEqualTo("PENDING");
    }

    @Test
    void create_duplicateIdempotencyKeyThrowsAndSkipsSave() {
        TopupRequest request = createRequest("C-001", 50000);
        when(topupRepository.findByIdempotencyKey("idem-new"))
                .thenReturn(Optional.of(new Topup()));

        assertThatThrownBy(() -> topupService.create(request))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Duplicate idempotency key");

        verify(topupRepository, never()).save(any(Topup.class));
        verify(outboxRepository, never()).save(any(Outbox.class));
    }

    @Test
    void create_withoutIdempotencyKey_skipsDuplicateLookupAndSaves() {
        TopupRequest request = new TopupRequest("C-001", 50000, "QRIS", null);
        when(topupRepository.save(any(Topup.class))).thenAnswer(inv -> {
            Topup topup = inv.getArgument(0);
            topup.setTopupId(8L);
            return topup;
        });

        Topup result = topupService.create(request);

        assertThat(result.getTopupId()).isEqualTo(8L);
        // Strict mocks double as an assertion: findByIdempotencyKey must never be consulted.
        verify(topupRepository).save(any(Topup.class));
        verify(outboxRepository).save(any(Outbox.class));
    }

    // ---- create: documents actual behavior — no amount validation beyond @NotNull ----

    @Test
    void create_acceptsZeroAmount() {
        when(topupRepository.findByIdempotencyKey("idem-new")).thenReturn(Optional.empty());
        when(topupRepository.save(any(Topup.class))).thenAnswer(inv -> {
            Topup topup = inv.getArgument(0);
            topup.setTopupId(9L);
            return topup;
        });

        Topup result = topupService.create(createRequest("C-001", 0));

        // service has no amount validation — 0 is persisted as-is
        assertThat(result.getTopupAmount()).isZero();
        verify(topupRepository).save(any(Topup.class));
    }

    @Test
    void create_acceptsNegativeAmount() {
        when(topupRepository.findByIdempotencyKey("idem-new")).thenReturn(Optional.empty());
        when(topupRepository.save(any(Topup.class))).thenAnswer(inv -> {
            Topup topup = inv.getArgument(0);
            topup.setTopupId(10L);
            return topup;
        });

        Topup result = topupService.create(createRequest("C-001", -100));

        // documents actual (unvalidated) behavior: negative amounts are accepted too
        assertThat(result.getTopupAmount()).isEqualTo(-100);
        verify(topupRepository).save(any(Topup.class));
    }

    // ---- create: outbox failure must not fail the business op ----

    @Test
    void create_outboxFailureIsSwallowed() {
        when(topupRepository.findByIdempotencyKey("idem-new")).thenReturn(Optional.empty());
        when(topupRepository.save(any(Topup.class))).thenAnswer(inv -> {
            Topup topup = inv.getArgument(0);
            topup.setTopupId(11L);
            return topup;
        });
        when(outboxRepository.save(any(Outbox.class))).thenThrow(new RuntimeException("kafka down"));

        Topup result = topupService.create(createRequest("C-001", 50000));

        assertThat(result.getTopupId()).isEqualTo(11L);
        verify(topupRepository).save(any(Topup.class));
    }

    @Test
    void create_outboxSerializationFailureIsSwallowed() {
        // Bare ObjectMapper lacks JavaTimeModule -> EventEnvelope(Instant) serialization
        // fails inside writeOutbox; the exception is swallowed and the topup is still
        // returned, but the outbox row is never persisted.
        TopupService bareMapperService = new TopupService(topupRepository, outboxRepository,
                topupMapper, new ObjectMapper(), OpenTelemetry.noop());
        when(topupRepository.findByIdempotencyKey("idem-new")).thenReturn(Optional.empty());
        when(topupRepository.save(any(Topup.class))).thenAnswer(inv -> {
            Topup topup = inv.getArgument(0);
            topup.setTopupId(12L);
            return topup;
        });

        Topup result = bareMapperService.create(createRequest("C-001", 50000));

        assertThat(result.getTopupId()).isEqualTo(12L);
        verify(topupRepository).save(any(Topup.class));
        verify(outboxRepository, never()).save(any(Outbox.class));
    }

    // ---- update: only cardNumber/amount/method ----

    @Test
    void update_changesOnlyCardNumberAmountAndMethod() {
        Topup existing = createTopup(1L, "C-001", 50000);
        existing.setStatus(Status.PENDING);
        when(topupRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(topupRepository.save(any(Topup.class))).thenAnswer(inv -> inv.getArgument(0));

        TopupRequest request = new TopupRequest("C-999", 250000, "QRIS", "idem-other");

        Topup result = topupService.update(1L, request);

        assertThat(result.getCardNumber()).isEqualTo("C-999");
        assertThat(result.getTopupAmount()).isEqualTo(250000);
        assertThat(result.getTopupMethod()).isEqualTo("QRIS");
        // immutable/not-updatable fields stay untouched
        assertThat(result.getTopupId()).isEqualTo(1L);
        assertThat(result.getTopupNo()).isEqualTo(existing.getTopupNo());
        assertThat(result.getIdempotencyKey()).isEqualTo("idem-1");
        assertThat(result.getStatus()).isEqualTo(Status.PENDING);
        verify(topupRepository).save(existing);
    }

    @Test
    void update_throwsWhenNotFound() {
        when(topupRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> topupService.update(999L, createRequest("C-001", 1)))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Topup not found");

        verify(topupRepository, never()).save(any(Topup.class));
    }

    // ---- delete ----

    @Test
    void delete_delegatesToRepository() {
        topupService.delete(1L);

        verify(topupRepository).deleteById(1L);
    }
}
