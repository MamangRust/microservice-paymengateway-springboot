package com.saldoservice.saldoservice.dto;

import java.time.LocalDateTime;

public record SaldoResponse(
    Long saldoId, String cardNumber, Integer totalBalance,
    Integer withdrawAmount, LocalDateTime withdrawTime,
    LocalDateTime createdAt, LocalDateTime updatedAt
) {}