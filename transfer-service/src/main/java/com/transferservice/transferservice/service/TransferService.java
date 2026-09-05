package com.transferservice.transferservice.service;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.metrics.DoubleHistogram;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import com.common.event.EventEnvelope;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import com.transferservice.transferservice.dto.TransferMapper;
import com.transferservice.transferservice.dto.TransferRequest;
import com.transferservice.transferservice.entity.Outbox;
import com.transferservice.transferservice.entity.OutboxStatus;
import com.transferservice.transferservice.entity.Transfer;
import com.transferservice.transferservice.repository.OutboxRepository;
import com.transferservice.transferservice.repository.TransferRepository;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class TransferService {
    private final TransferRepository repository;
    private final OutboxRepository outboxRepository;
    private final TransferMapper mapper;
    private final ObjectMapper objectMapper;
    private final Tracer tracer;
    public TransferService(TransferRepository repository, OutboxRepository outboxRepository, TransferMapper mapper,
                           ObjectMapper objectMapper, OpenTelemetry openTelemetry) {
        this.repository = repository;
        this.outboxRepository = outboxRepository;
        this.mapper = mapper;
        this.objectMapper = objectMapper;
        this.tracer = openTelemetry.getTracer("transfer-service", "1.0.0");
        Meter meter = openTelemetry.getMeter("transfer-service");
        meter.counterBuilder("requests_total").build();
        meter.histogramBuilder("requests_duration_seconds").setUnit("s").build();
    }
    public List<Transfer> getAll() { Span span = tracer.spanBuilder("getAll").startSpan(); try (Scope s = span.makeCurrent()) { return repository.findAll(); } finally { span.end(); } }
    public Transfer getById(Long id) { return repository.findById(id).orElseThrow(() -> new RuntimeException("Transfer not found")); }
    public Transfer create(TransferRequest req) {
        if (req.idempotencyKey() != null && repository.findByIdempotencyKey(req.idempotencyKey()).isPresent()) {
            throw new RuntimeException("Duplicate idempotency key");
        }
        Transfer saved = repository.save(mapper.toEntity(req));
        writeOutbox(saved);
        return saved;
    }
    public Transfer update(Long id, TransferRequest req) {
        Transfer e = getById(id);
        e.setTransferFrom(req.transferFrom()); e.setTransferTo(req.transferTo()); e.setTransferAmount(req.transferAmount());
        return repository.save(e);
    }
    public void delete(Long id) { repository.deleteById(id); }

    private void writeOutbox(Transfer transfer) {
        try {
            Map<String, Object> payload = Map.of(
                "eventId", UUID.randomUUID().toString(),
                "transferId", transfer.getTransferId(),
                "transferFrom", transfer.getTransferFrom() == null ? "" : transfer.getTransferFrom(),
                "transferTo", transfer.getTransferTo() == null ? "" : transfer.getTransferTo(),
                "amount", transfer.getTransferAmount() == null ? 0 : transfer.getTransferAmount(),
                "status", transfer.getStatus() == null ? "PENDING" : transfer.getStatus().name()
            );
            EventEnvelope<Map<String, Object>> envelope =
                EventEnvelope.withDefaults(payload, "transfer.created", "transfer");
            Outbox outbox = new Outbox();
            outbox.setAggregateType("Transfer");
            outbox.setAggregateId(String.valueOf(transfer.getTransferId()));
            outbox.setTopic("stats.payment.transfer.event");
            outbox.setPayload(objectMapper.writeValueAsString(envelope));
            outbox.setStatus(OutboxStatus.PENDING);
            outbox.setDomain("transfer");
            outbox.setEventId(envelope.eventId());
            outboxRepository.save(outbox);
        } catch (Exception e) {
            // outbox write failure must not break the business op
        }
    }
}