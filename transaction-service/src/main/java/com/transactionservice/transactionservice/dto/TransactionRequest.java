package com.transactionservice.transactionservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record TransactionRequest(
    @NotBlank String cardNumber,
    @NotNull Integer amount,
    String paymentMethod,
    Integer merchantId,
    String idempotencyKey
) {}