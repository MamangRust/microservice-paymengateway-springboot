package com.topupservice.topupservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record TopupRequest(
    @NotBlank String cardNumber,
    @NotNull Integer topupAmount,
    String topupMethod,
    String idempotencyKey
) {}