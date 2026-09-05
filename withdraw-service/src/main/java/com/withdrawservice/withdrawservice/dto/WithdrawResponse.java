package com.withdrawservice.withdrawservice.dto;

import java.time.LocalDateTime;

public record WithdrawResponse(
    Long withdrawId, String withdrawNo, String cardNumber, Integer withdrawAmount,
    LocalDateTime withdrawTime, String status,
    LocalDateTime createdAt, LocalDateTime updatedAt
) {}