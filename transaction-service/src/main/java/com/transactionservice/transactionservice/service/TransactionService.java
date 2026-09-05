package com.transactionservice.transactionservice.service;

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
import com.transactionservice.transactionservice.dto.TransactionMapper;
import com.transactionservice.transactionservice.dto.TransactionRequest;
import com.transactionservice.transactionservice.entity.Outbox;
import com.transactionservice.transactionservice.entity.OutboxStatus;
import com.transactionservice.transactionservice.entity.Transaction;
import com.transactionservice.transactionservice.repository.OutboxRepository;
import com.transactionservice.transactionservice.repository.TransactionRepository;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class TransactionService {
    private final TransactionRepository repository;
    private final OutboxRepository outboxRepository;
    private final TransactionMapper mapper;
    private final ObjectMapper objectMapper;
    private final Tracer tracer;
    public TransactionService(TransactionRepository repository, OutboxRepository outboxRepository,
                              TransactionMapper mapper, ObjectMapper objectMapper, OpenTelemetry openTelemetry) {
        this.repository = repository;
        this.outboxRepository = outboxRepository;
        this.mapper = mapper;
        this.objectMapper = objectMapper;
        this.tracer = openTelemetry.getTracer("transaction-service", "1.0.0");
        Meter meter = openTelemetry.getMeter("transaction-service");
        meter.counterBuilder("requests_total").build();
        meter.histogramBuilder("requests_duration_seconds").setUnit("s").build();
    }
    public List<Transaction> getAll() { Span span = tracer.spanBuilder("getAll").startSpan(); try (Scope s = span.makeCurrent()) { return repository.findAll(); } finally { span.end(); } }
    public Transaction getById(Long id) { return repository.findById(id).orElseThrow(() -> new RuntimeException("Transaction not found")); }
    public Transaction create(TransactionRequest req) {
        if (req.idempotencyKey() != null && repository.findByIdempotencyKey(req.idempotencyKey()).isPresent()) {
            throw new RuntimeException("Duplicate idempotency key");
        }
        Transaction saved = repository.save(mapper.toEntity(req));
        writeOutbox(saved);
        return saved;
    }
    public Transaction update(Long id, TransactionRequest req) {
        Transaction e = getById(id);
        e.setCardNumber(req.cardNumber()); e.setAmount(req.amount());
        e.setPaymentMethod(req.paymentMethod()); e.setMerchantId(req.merchantId());
        return repository.save(e);
    }
    public void delete(Long id) { repository.deleteById(id); }

    private void writeOutbox(Transaction txn) {
        try {
            Map<String, Object> payload = Map.of(
                "eventId", UUID.randomUUID().toString(),
                "transactionId", txn.getTransactionId(),
                "cardNumber", txn.getCardNumber() == null ? "" : txn.getCardNumber(),
                "amount", txn.getAmount() == null ? 0 : txn.getAmount(),
                "paymentMethod", txn.getPaymentMethod() == null ? "" : txn.getPaymentMethod(),
                "status", txn.getStatus() == null ? "PENDING" : txn.getStatus().name()
            );
            EventEnvelope<Map<String, Object>> envelope =
                EventEnvelope.withDefaults(payload, "transaction.created", "transaction");
            Outbox outbox = new Outbox();
            outbox.setAggregateType("Transaction");
            outbox.setAggregateId(String.valueOf(txn.getTransactionId()));
            outbox.setTopic("stats.payment.transaction.event");
            outbox.setPayload(objectMapper.writeValueAsString(envelope));
            outbox.setStatus(OutboxStatus.PENDING);
            outbox.setDomain("transaction");
            outbox.setEventId(envelope.eventId());
            outboxRepository.save(outbox);
        } catch (Exception e) {
            // outbox write failure must not break the business op
        }
    }
}