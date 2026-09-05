package com.transferservice.transferservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record TransferRequest(
    @NotBlank String transferFrom,
    @NotBlank String transferTo,
    @NotNull Integer transferAmount,
    String idempotencyKey
) {}