package com.withdrawservice.withdrawservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record WithdrawRequest(
    @NotBlank String cardNumber,
    @NotNull Integer withdrawAmount,
    String idempotencyKey
) {}