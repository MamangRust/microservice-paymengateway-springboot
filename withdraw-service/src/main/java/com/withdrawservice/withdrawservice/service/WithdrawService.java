package com.withdrawservice.withdrawservice.service;

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
import com.withdrawservice.withdrawservice.dto.WithdrawMapper;
import com.withdrawservice.withdrawservice.dto.WithdrawRequest;
import com.withdrawservice.withdrawservice.entity.Outbox;
import com.withdrawservice.withdrawservice.entity.OutboxStatus;
import com.withdrawservice.withdrawservice.entity.Withdraw;
import com.withdrawservice.withdrawservice.repository.OutboxRepository;
import com.withdrawservice.withdrawservice.repository.WithdrawRepository;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class WithdrawService {
    private final WithdrawRepository repository;
    private final OutboxRepository outboxRepository;
    private final WithdrawMapper mapper;
    private final ObjectMapper objectMapper;
    private final Tracer tracer;
    public WithdrawService(WithdrawRepository repository, OutboxRepository outboxRepository, WithdrawMapper mapper,
                           ObjectMapper objectMapper, OpenTelemetry openTelemetry) {
        this.repository = repository;
        this.outboxRepository = outboxRepository;
        this.mapper = mapper;
        this.objectMapper = objectMapper;
        this.tracer = openTelemetry.getTracer("withdraw-service", "1.0.0");
        Meter meter = openTelemetry.getMeter("withdraw-service");
        meter.counterBuilder("requests_total").build();
        meter.histogramBuilder("requests_duration_seconds").setUnit("s").build();
    }
    public List<Withdraw> getAll() { Span span = tracer.spanBuilder("getAll").startSpan(); try (Scope s = span.makeCurrent()) { return repository.findAll(); } finally { span.end(); } }
    public Withdraw getById(Long id) { return repository.findById(id).orElseThrow(() -> new RuntimeException("Withdraw not found")); }
    public Withdraw create(WithdrawRequest req) {
        if (req.idempotencyKey() != null && repository.findByIdempotencyKey(req.idempotencyKey()).isPresent()) {
            throw new RuntimeException("Duplicate idempotency key");
        }
        Withdraw saved = repository.save(mapper.toEntity(req));
        writeOutbox(saved);
        return saved;
    }
    public Withdraw update(Long id, WithdrawRequest req) {
        Withdraw e = getById(id);
        e.setCardNumber(req.cardNumber()); e.setWithdrawAmount(req.withdrawAmount());
        return repository.save(e);
    }
    public void delete(Long id) { repository.deleteById(id); }

    private void writeOutbox(Withdraw withdraw) {
        try {
            Map<String, Object> payload = Map.of(
                "eventId", UUID.randomUUID().toString(),
                "withdrawId", withdraw.getWithdrawId(),
                "cardNumber", withdraw.getCardNumber() == null ? "" : withdraw.getCardNumber(),
                "amount", withdraw.getWithdrawAmount() == null ? 0 : withdraw.getWithdrawAmount(),
                "status", withdraw.getStatus() == null ? "PENDING" : withdraw.getStatus().name()
            );
            EventEnvelope<Map<String, Object>> envelope =
                EventEnvelope.withDefaults(payload, "withdraw.created", "withdraw");
            Outbox outbox = new Outbox();
            outbox.setAggregateType("Withdraw");
            outbox.setAggregateId(String.valueOf(withdraw.getWithdrawId()));
            outbox.setTopic("stats.payment.withdraw.event");
            outbox.setPayload(objectMapper.writeValueAsString(envelope));
            outbox.setStatus(OutboxStatus.PENDING);
            outbox.setDomain("withdraw");
            outbox.setEventId(envelope.eventId());
            outboxRepository.save(outbox);
        } catch (Exception e) {
            // outbox write failure must not break the business op
        }
    }
}