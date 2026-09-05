package com.transferservice.transferservice.dto;

import java.time.LocalDateTime;

public record TransferResponse(
    Long transferId, String transferNo, String transferFrom, String transferTo,
    Integer transferAmount, LocalDateTime transferTime, String status,
    LocalDateTime createdAt, LocalDateTime updatedAt
) {}