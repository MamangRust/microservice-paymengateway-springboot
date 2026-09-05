package com.cardservice.cardservice.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "cards")
public class Card {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long cardId;

    @Column(name = "user_id")
    private Integer userId;

    @Column(name = "card_number", unique = true)
    private String cardNumber;

    @Column(name = "card_type")
    private String cardType;

    @Column(name = "expire_date")
    private LocalDate expireDate;

    @Column(name = "cvv")
    private String cvv;

    @Column(name = "card_provider")
    private String cardProvider;

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private CardStatus status = CardStatus.ACTIVE;

    @Column(name = "credit_limit", precision = 15, scale = 2)
    private BigDecimal creditLimit;

    @Column(name = "points", precision = 15, scale = 2)
    private BigDecimal points;

    @Column(name = "created_at", updatable = false) private LocalDateTime createdAt;
    @Column(name = "updated_at") private LocalDateTime updatedAt;
    @Column(name = "deleted_at") private LocalDateTime deletedAt;

    @PrePersist
    protected void onCreate() { createdAt = LocalDateTime.now(); updatedAt = LocalDateTime.now(); }
    @PreUpdate
    protected void onUpdate() { updatedAt = LocalDateTime.now(); }
}