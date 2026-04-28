package com.example.tdd.messaging;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record OrderEvent(
    UUID orderId,
    String type,
    String userId,
    BigDecimal total,
    Instant occurredAt
) {}
