package com.topupservice.topupservice.dto;

public record ErrorResponse(int status, String error, String message, String path) {}