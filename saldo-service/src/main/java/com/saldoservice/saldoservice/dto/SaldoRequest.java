package com.saldoservice.saldoservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record SaldoRequest(
    @NotBlank String cardNumber,
    @NotNull Integer totalBalance
) {}