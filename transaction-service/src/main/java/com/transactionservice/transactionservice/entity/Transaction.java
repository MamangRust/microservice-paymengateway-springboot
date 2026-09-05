package com.transactionservice.transactionservice.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Entity
@Table(name = "transactions")
public class Transaction {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long transactionId;

    @Column(name = "transaction_no", unique = true)
    private String transactionNo = UUID.randomUUID().toString();

    @Column(name = "card_number")
    private String cardNumber;

    @Column(name = "amount")
    private Integer amount;

    @Column(name = "payment_method")
    private String paymentMethod;

    @Column(name = "merchant_id")
    private Integer merchantId;

    @Column(name = "transaction_time")
    private LocalDateTime transactionTime;

    @Column(name = "idempotency_key", unique = true)
    private String idempotencyKey;

    @Column(name = "request_fingerprint")
    private String requestFingerprint;

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private Status status = Status.PENDING;

    @Column(name = "created_at", updatable = false) private LocalDateTime createdAt;
    @Column(name = "updated_at") private LocalDateTime updatedAt;
    @Column(name = "deleted_at") private LocalDateTime deletedAt;

    @PrePersist
    protected void onCreate() { createdAt = LocalDateTime.now(); updatedAt = LocalDateTime.now(); }
    @PreUpdate
    protected void onUpdate() { updatedAt = LocalDateTime.now(); }
}