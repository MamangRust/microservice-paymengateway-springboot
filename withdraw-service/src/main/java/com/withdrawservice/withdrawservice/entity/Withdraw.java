package com.withdrawservice.withdrawservice.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Entity
@Table(name = "withdraws")
public class Withdraw {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long withdrawId;

    @Column(name = "withdraw_no", unique = true)
    private String withdrawNo = UUID.randomUUID().toString();

    @Column(name = "card_number")
    private String cardNumber;

    @Column(name = "withdraw_amount")
    private Integer withdrawAmount;

    @Column(name = "withdraw_time")
    private LocalDateTime withdrawTime;

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