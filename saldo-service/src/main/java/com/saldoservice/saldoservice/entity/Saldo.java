package com.saldoservice.saldoservice.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "saldos")
public class Saldo {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long saldoId;

    @Column(name = "card_number", nullable = false)
    private String cardNumber;

    @Column(name = "total_balance")
    private Integer totalBalance = 0;

    @Column(name = "withdraw_amount")
    private Integer withdrawAmount;

    @Column(name = "withdraw_time")
    private LocalDateTime withdrawTime;

    @Column(name = "created_at", updatable = false) private LocalDateTime createdAt;
    @Column(name = "updated_at") private LocalDateTime updatedAt;
    @Column(name = "deleted_at") private LocalDateTime deletedAt;

    @PrePersist
    protected void onCreate() { createdAt = LocalDateTime.now(); updatedAt = LocalDateTime.now(); }
    @PreUpdate
    protected void onUpdate() { updatedAt = LocalDateTime.now(); }
}