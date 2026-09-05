package com.topupservice.topupservice.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Entity
@Table(name = "topups")
public class Topup {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long topupId;

    @Column(name = "topup_no", unique = true)
    private String topupNo = UUID.randomUUID().toString();

    @Column(name = "card_number")
    private String cardNumber;

    @Column(name = "topup_amount")
    private Integer topupAmount;

    @Column(name = "topup_method")
    private String topupMethod;

    @Column(name = "topup_time")
    private LocalDateTime topupTime;

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