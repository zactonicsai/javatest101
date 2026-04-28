package com.example.tdd.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record CreateOrderRequest(
    @NotBlank(message = "userId is required")
    String userId,

    @NotEmpty(message = "items must contain at least one entry")
    @Size(max = 100, message = "too many items")
    @Valid
    List<OrderLine> items,

    @Email
    @Size(max = 255)
    String contactEmail,

    @PastOrPresent
    LocalDate orderDate,

    @Size(max = 255)
    String idempotencyKey
) {
    public record OrderLine(
        @NotBlank
        @Size(max = 64)
        String sku,

        @Positive
        @Max(value = 100, message = "quantity must be ≤ 100 per line")
        int quantity,

        @NotNull
        @PositiveOrZero
        BigDecimal unitPrice
    ) {}
}
