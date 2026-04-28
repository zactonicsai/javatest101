package com.example.tdd.api.dto;

import com.example.tdd.domain.order.Order;
import com.example.tdd.domain.order.OrderLine;
import com.example.tdd.domain.order.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OrderResponse(
    UUID id,
    String userId,
    OrderStatus status,
    List<LineResponse> lines,
    BigDecimal total,
    String contactEmail,
    Instant createdAt
) {
    public static OrderResponse from(Order entity) {
        return new OrderResponse(
            entity.getId(),
            entity.getUserId(),
            entity.getStatus(),
            entity.getLines().stream().map(LineResponse::from).toList(),
            entity.total(),
            entity.getContactEmail(),
            entity.getCreatedAt()
        );
    }

    public record LineResponse(String sku, int quantity, BigDecimal unitPrice, BigDecimal lineTotal) {
        static LineResponse from(OrderLine l) {
            return new LineResponse(
                l.getSku(),
                l.getQuantity(),
                l.getUnitPrice(),
                l.lineTotal()
            );
        }
    }
}
