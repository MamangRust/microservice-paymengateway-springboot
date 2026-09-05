package com.cardservice.cardservice.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "card_auth_transactions")
public class CardAuthTransaction {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long authTxnId;

    @Column(name = "card_number")
    private String cardNumber;

    @Column(name = "merchant_id")
    private Integer merchantId;

    @Column(name = "amount", precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(name = "currency")
    private String currency;

    @Column(name = "pos_entry_mode")
    private String posEntryMode;

    @Column(name = "mcc")
    private String mcc;

    @Column(name = "idempotency_key", unique = true)
    private String idempotencyKey;

    @Column(name = "risk_score")
    private Integer riskScore;

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private AuthStatus status = AuthStatus.PENDING;

    @Column(name = "authorized_at")
    private LocalDateTime authorizedAt;

    @Column(name = "reversed_at")
    private LocalDateTime reversedAt;

    @Column(name = "created_at", updatable = false) private LocalDateTime createdAt;
    @Column(name = "updated_at") private LocalDateTime updatedAt;
    @Column(name = "deleted_at") private LocalDateTime deletedAt;

    @PrePersist
    protected void onCreate() { createdAt = LocalDateTime.now(); updatedAt = LocalDateTime.now(); }
    @PreUpdate
    protected void onUpdate() { updatedAt = LocalDateTime.now(); }
}