package com.cardservice.cardservice.service;

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
import com.cardservice.cardservice.dto.CardMapper;
import com.cardservice.cardservice.dto.CardRequest;
import com.cardservice.cardservice.entity.Card;
import com.cardservice.cardservice.entity.CardAuthTransaction;
import com.cardservice.cardservice.entity.CardStatus;
import com.cardservice.cardservice.entity.Outbox;
import com.cardservice.cardservice.entity.OutboxStatus;
import com.cardservice.cardservice.repository.CardRepository;
import com.cardservice.cardservice.repository.CardAuthTransactionRepository;
import com.cardservice.cardservice.repository.OutboxRepository;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class CardService {
    private final CardRepository cardRepository;
    private final CardAuthTransactionRepository authTxnRepository;
    private final OutboxRepository outboxRepository;
    private final CardMapper mapper;
    private final ObjectMapper objectMapper;
    private final Tracer tracer;
    private final LongCounter requestsTotal;
    private final DoubleHistogram requestsDuration;

    public CardService(CardRepository cardRepository, CardAuthTransactionRepository authTxnRepository,
                       OutboxRepository outboxRepository, CardMapper mapper, ObjectMapper objectMapper,
                       OpenTelemetry openTelemetry) {
        this.cardRepository = cardRepository;
        this.authTxnRepository = authTxnRepository;
        this.outboxRepository = outboxRepository;
        this.mapper = mapper;
        this.objectMapper = objectMapper;
        this.tracer = openTelemetry.getTracer("card-service", "1.0.0");
        Meter meter = openTelemetry.getMeter("card-service");
        this.requestsTotal = meter.counterBuilder("requests_total").setDescription("Total").setUnit("1").build();
        this.requestsDuration = meter.histogramBuilder("requests_duration_seconds").setDescription("Duration").setUnit("s").build();
    }

    public List<Card> getAllCards() {
        Span span = tracer.spanBuilder("getAllCards").setSpanKind(SpanKind.SERVER).startSpan();
        try (Scope scope = span.makeCurrent()) { return cardRepository.findAll(); }
        finally { span.end(); }
    }

    public Card getCardById(Long id) { return cardRepository.findById(id).orElseThrow(() -> new RuntimeException("Card not found")); }
    public Card getByCardNumber(String cardNumber) { return cardRepository.findByCardNumber(cardNumber).orElseThrow(() -> new RuntimeException("Card not found")); }
    public List<Card> getByUserId(Integer userId) { return cardRepository.findByUserId(userId); }
    public Card createCard(CardRequest req) {
        Card saved = cardRepository.save(mapper.toEntity(req));
        writeOutbox(saved);
        return saved;
    }

    private void writeOutbox(Card card) {
        try {
            Map<String, Object> payload = Map.of(
                "eventId", UUID.randomUUID().toString(),
                "cardId", card.getCardId(),
                "cardNumber", card.getCardNumber() == null ? "" : card.getCardNumber(),
                "userId", card.getUserId() == null ? 0 : card.getUserId(),
                "status", card.getStatus() == null ? "ACTIVE" : card.getStatus().name()
            );
            EventEnvelope<Map<String, Object>> envelope =
                EventEnvelope.withDefaults(payload, "card.created", "card");
            Outbox outbox = new Outbox();
            outbox.setAggregateType("Card");
            outbox.setAggregateId(String.valueOf(card.getCardId()));
            outbox.setTopic("stats.payment.card.event");
            outbox.setPayload(objectMapper.writeValueAsString(envelope));
            outbox.setStatus(OutboxStatus.PENDING);
            outbox.setDomain("card");
            outbox.setEventId(envelope.eventId());
            outboxRepository.save(outbox);
        } catch (Exception e) {
            // outbox write failure must not break the business op
        }
    }

    public Card updateCard(Long id, CardRequest req) {
        Card c = getCardById(id);
        c.setCardType(req.cardType()); c.setExpireDate(req.expireDate()); c.setCardProvider(req.cardProvider());
        c.setCreditLimit(req.creditLimit()); c.setPoints(req.points());
        return cardRepository.save(c);
    }

    public void deleteCard(Long id) { cardRepository.deleteById(id); }

    public CardAuthTransaction authorize(CardAuthTransaction txn) {
        if (txn.getIdempotencyKey() != null && authTxnRepository.findByIdempotencyKey(txn.getIdempotencyKey()).isPresent()) {
            throw new RuntimeException("Duplicate idempotency key");
        }
        return authTxnRepository.save(txn);
    }

    public List<CardAuthTransaction> getAuthTransactions(String cardNumber) {
        return authTxnRepository.findByCardNumber(cardNumber);
    }
}