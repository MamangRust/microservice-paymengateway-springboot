package com.cardservice.cardservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;

public record CardRequest(
    @NotNull Integer userId,
    @NotBlank String cardNumber,
    String cardType,
    LocalDate expireDate,
    String cvv,
    String cardProvider,
    BigDecimal creditLimit,
    BigDecimal points
) {}