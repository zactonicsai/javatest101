package com.example.tdd.support;

import com.example.tdd.api.dto.CreateOrderRequest;
import com.example.tdd.domain.order.Order;
import com.example.tdd.domain.order.OrderLine;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public final class OrderFixtures {

    private OrderFixtures() {}

    public static Order simple(String userId) {
        return Order.newOrder(userId, List.of(
            new OrderLine("SKU-1", 2, new BigDecimal("9.99"), 0),
            new OrderLine("SKU-2", 1, new BigDecimal("5.50"), 1)
        ));
    }

    public static Order singleLine(String userId, String sku, int qty, String price) {
        return Order.newOrder(userId, List.of(
            new OrderLine(sku, qty, new BigDecimal(price), 0)
        ));
    }

    public static CreateOrderRequest validRequest() {
        return new CreateOrderRequest(
            "USR-1",
            List.of(new CreateOrderRequest.OrderLine("SKU-1", 2, new BigDecimal("9.99"))),
            "alice@example.com",
            LocalDate.now(),
            null
        );
    }

    public static CreateOrderRequest validRequestFor(String userId) {
        return new CreateOrderRequest(
            userId,
            List.of(new CreateOrderRequest.OrderLine("SKU-1", 2, new BigDecimal("9.99"))),
            "user@example.com",
            LocalDate.now(),
            null
        );
    }
}
