package com.transactionservice.transactionservice.dto;

import java.time.LocalDateTime;

public record TransactionResponse(
    Long transactionId, String transactionNo, String cardNumber, Integer amount,
    String paymentMethod, Integer merchantId, LocalDateTime transactionTime, String status,
    LocalDateTime createdAt, LocalDateTime updatedAt
) {}