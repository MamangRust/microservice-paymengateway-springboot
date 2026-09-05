package com.cardservice.cardservice.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record CardResponse(
    Long cardId, Integer userId, String cardNumber, String cardType,
    LocalDate expireDate, String cardProvider, String status,
    BigDecimal creditLimit, BigDecimal points,
    LocalDateTime createdAt, LocalDateTime updatedAt
) {}