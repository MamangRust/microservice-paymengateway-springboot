package com.topupservice.topupservice.service;

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
import com.topupservice.topupservice.dto.TopupMapper;
import com.topupservice.topupservice.dto.TopupRequest;
import com.topupservice.topupservice.entity.Outbox;
import com.topupservice.topupservice.entity.OutboxStatus;
import com.topupservice.topupservice.entity.Topup;
import com.topupservice.topupservice.repository.OutboxRepository;
import com.topupservice.topupservice.repository.TopupRepository;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class TopupService {
    private final TopupRepository repository;
    private final OutboxRepository outboxRepository;
    private final TopupMapper mapper;
    private final ObjectMapper objectMapper;
    private final Tracer tracer;
    public TopupService(TopupRepository repository, OutboxRepository outboxRepository, TopupMapper mapper,
                        ObjectMapper objectMapper, OpenTelemetry openTelemetry) {
        this.repository = repository;
        this.outboxRepository = outboxRepository;
        this.mapper = mapper;
        this.objectMapper = objectMapper;
        this.tracer = openTelemetry.getTracer("topup-service", "1.0.0");
        Meter meter = openTelemetry.getMeter("topup-service");
        meter.counterBuilder("requests_total").build();
        meter.histogramBuilder("requests_duration_seconds").setUnit("s").build();
    }
    public List<Topup> getAll() { Span span = tracer.spanBuilder("getAll").startSpan(); try (Scope s = span.makeCurrent()) { return repository.findAll(); } finally { span.end(); } }
    public Topup getById(Long id) { return repository.findById(id).orElseThrow(() -> new RuntimeException("Topup not found")); }
    public Topup create(TopupRequest req) {
        if (req.idempotencyKey() != null && repository.findByIdempotencyKey(req.idempotencyKey()).isPresent()) {
            throw new RuntimeException("Duplicate idempotency key");
        }
        Topup saved = repository.save(mapper.toEntity(req));
        writeOutbox(saved);
        return saved;
    }
    public Topup update(Long id, TopupRequest req) {
        Topup e = getById(id);
        e.setCardNumber(req.cardNumber()); e.setTopupAmount(req.topupAmount()); e.setTopupMethod(req.topupMethod());
        return repository.save(e);
    }
    public void delete(Long id) { repository.deleteById(id); }

    private void writeOutbox(Topup topup) {
        try {
            Map<String, Object> payload = Map.of(
                "eventId", UUID.randomUUID().toString(),
                "topupId", topup.getTopupId(),
                "cardNumber", topup.getCardNumber() == null ? "" : topup.getCardNumber(),
                "amount", topup.getTopupAmount() == null ? 0 : topup.getTopupAmount(),
                "status", topup.getStatus() == null ? "PENDING" : topup.getStatus().name()
            );
            EventEnvelope<Map<String, Object>> envelope =
                EventEnvelope.withDefaults(payload, "topup.created", "topup");
            Outbox outbox = new Outbox();
            outbox.setAggregateType("Topup");
            outbox.setAggregateId(String.valueOf(topup.getTopupId()));
            outbox.setTopic("stats.payment.topup.event");
            outbox.setPayload(objectMapper.writeValueAsString(envelope));
            outbox.setStatus(OutboxStatus.PENDING);
            outbox.setDomain("topup");
            outbox.setEventId(envelope.eventId());
            outboxRepository.save(outbox);
        } catch (Exception e) {
            // outbox write failure must not break the business op
        }
    }
}