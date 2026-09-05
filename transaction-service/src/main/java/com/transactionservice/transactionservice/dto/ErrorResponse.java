package com.transactionservice.transactionservice.dto;

public record ErrorResponse(int status, String error, String message, String path) {}