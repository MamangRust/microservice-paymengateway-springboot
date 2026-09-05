package com.withdrawservice.withdrawservice.dto;

public record ErrorResponse(int status, String error, String message, String path) {}