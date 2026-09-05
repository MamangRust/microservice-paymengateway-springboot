package com.topupservice.topupservice.dto;

import java.time.LocalDateTime;

public record TopupResponse(
    Long topupId, String topupNo, String cardNumber, Integer topupAmount,
    String topupMethod, LocalDateTime topupTime, String status,
    LocalDateTime createdAt, LocalDateTime updatedAt
) {}