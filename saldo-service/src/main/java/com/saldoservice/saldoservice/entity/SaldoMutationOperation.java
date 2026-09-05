package com.saldoservice.saldoservice.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "saldo_mutation_operations")
public class SaldoMutationOperation {
    @Id
    @Column(name = "operation_key")
    private String operationKey;

    @Column(name = "card_number", nullable = false)
    private String cardNumber;

    @Column(name = "requested_delta")
    private Integer requestedDelta;

    @Column(name = "minimum_balance")
    private Integer minimumBalance;

    @Column(name = "result_status")
    private String resultStatus;

    @Column(name = "result_balance")
    private Integer resultBalance;

    @Column(name = "failure_reason")
    private String failureReason;

    @Column(name = "created_at", updatable = false) private LocalDateTime createdAt;
    @Column(name = "updated_at") private LocalDateTime updatedAt;
    @Column(name = "deleted_at") private LocalDateTime deletedAt;

    @PrePersist
    protected void onCreate() { createdAt = LocalDateTime.now(); updatedAt = LocalDateTime.now(); }
    @PreUpdate
    protected void onUpdate() { updatedAt = LocalDateTime.now(); }
}