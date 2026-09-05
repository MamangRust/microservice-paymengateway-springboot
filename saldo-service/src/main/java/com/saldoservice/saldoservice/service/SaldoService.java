package com.saldoservice.saldoservice.service;

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
import org.springframework.transaction.annotation.Transactional;
import com.saldoservice.saldoservice.dto.SaldoMapper;
import com.saldoservice.saldoservice.dto.SaldoRequest;
import com.saldoservice.saldoservice.entity.Outbox;
import com.saldoservice.saldoservice.entity.OutboxStatus;
import com.saldoservice.saldoservice.entity.Saldo;
import com.saldoservice.saldoservice.entity.SaldoMutationOperation;
import com.saldoservice.saldoservice.repository.OutboxRepository;
import com.saldoservice.saldoservice.repository.SaldoMutationOperationRepository;
import com.saldoservice.saldoservice.repository.SaldoRepository;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class SaldoService {
    private final SaldoRepository saldoRepository;
    private final SaldoMutationOperationRepository mutationRepository;
    private final OutboxRepository outboxRepository;
    private final SaldoMapper mapper;
    private final ObjectMapper objectMapper;
    private final Tracer tracer;

    public SaldoService(SaldoRepository saldoRepository, SaldoMutationOperationRepository mutationRepository,
                        OutboxRepository outboxRepository, SaldoMapper mapper, ObjectMapper objectMapper,
                        OpenTelemetry openTelemetry) {
        this.saldoRepository = saldoRepository;
        this.mutationRepository = mutationRepository;
        this.outboxRepository = outboxRepository;
        this.mapper = mapper;
        this.objectMapper = objectMapper;
        this.tracer = openTelemetry.getTracer("saldo-service", "1.0.0");
        Meter meter = openTelemetry.getMeter("saldo-service");
        meter.counterBuilder("requests_total").build();
        meter.histogramBuilder("requests_duration_seconds").setUnit("s").build();
    }

    public List<Saldo> getAll() {
        Span span = tracer.spanBuilder("getAllSaldos").setSpanKind(SpanKind.SERVER).startSpan();
        try (Scope s = span.makeCurrent()) { return saldoRepository.findAll(); }
        finally { span.end(); }
    }

    public Saldo getById(Long id) { return saldoRepository.findById(id).orElseThrow(() -> new RuntimeException("Saldo not found")); }
    public Saldo getByCardNumber(String cardNumber) { return saldoRepository.findByCardNumber(cardNumber).orElseThrow(() -> new RuntimeException("Saldo not found")); }
    public Saldo create(SaldoRequest req) {
        Saldo saved = saldoRepository.save(mapper.toEntity(req));
        writeOutbox(saved);
        return saved;
    }

    private void writeOutbox(Saldo saldo) {
        try {
            Map<String, Object> payload = Map.of(
                "eventId", UUID.randomUUID().toString(),
                "saldoId", saldo.getSaldoId(),
                "cardNumber", saldo.getCardNumber() == null ? "" : saldo.getCardNumber(),
                "totalBalance", saldo.getTotalBalance() == null ? 0 : saldo.getTotalBalance()
            );
            EventEnvelope<Map<String, Object>> envelope =
                EventEnvelope.withDefaults(payload, "saldo.created", "saldo");
            Outbox outbox = new Outbox();
            outbox.setAggregateType("Saldo");
            outbox.setAggregateId(String.valueOf(saldo.getSaldoId()));
            outbox.setTopic("stats.payment.saldo.event");
            outbox.setPayload(objectMapper.writeValueAsString(envelope));
            outbox.setStatus(OutboxStatus.PENDING);
            outbox.setDomain("saldo");
            outbox.setEventId(envelope.eventId());
            outboxRepository.save(outbox);
        } catch (Exception e) {
            // outbox write failure must not break the business op
        }
    }

    /**
     * Idempotent saldo mutation — mirrors Quarkus saldo_mutation_operations ledger.
     * Each operation_key is applied at most once.
     */
    @Transactional
    public SaldoMutationOperation mutate(String operationKey, String cardNumber, int delta, int minimumBalance) {
        if (mutationRepository.findByOperationKey(operationKey).isPresent()) {
            throw new RuntimeException("Duplicate operation key");
        }
        Saldo saldo = saldoRepository.findByCardNumber(cardNumber)
            .orElseThrow(() -> new RuntimeException("Saldo not found"));
        int newBalance = saldo.getTotalBalance() + delta;
        if (newBalance < minimumBalance) {
            throw new RuntimeException("Insufficient balance");
        }
        saldo.setTotalBalance(newBalance);
        saldoRepository.save(saldo);

        SaldoMutationOperation op = new SaldoMutationOperation();
        op.setOperationKey(operationKey);
        op.setCardNumber(cardNumber);
        op.setRequestedDelta(delta);
        op.setMinimumBalance(minimumBalance);
        op.setResultStatus("SUCCESS");
        op.setResultBalance(newBalance);
        return mutationRepository.save(op);
    }

    public Saldo update(Long id, SaldoRequest req) {
        Saldo s = getById(id);
        s.setCardNumber(req.cardNumber());
        s.setTotalBalance(req.totalBalance());
        return saldoRepository.save(s);
    }

    public void delete(Long id) { saldoRepository.deleteById(id); }
}