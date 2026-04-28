package com.example.tdd.api.dto;

import java.util.List;

public record ApiError(
    String code,
    String message,
    List<FieldError> errors
) {
    public ApiError(String code, String message) {
        this(code, message, List.of());
    }

    public record FieldError(String field, String message, String rejectedValue) {}
}
