package com.example.tdd.api;

import com.example.tdd.api.dto.CreateOrderRequest;
import com.example.tdd.api.dto.OrderResponse;
import com.example.tdd.domain.exception.NotFoundException;
import com.example.tdd.domain.order.OrderService;
import com.example.tdd.domain.order.OrderStatus;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/orders")
@Validated
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('USER')")
    public OrderResponse create(@Valid @RequestBody CreateOrderRequest request,
                                @AuthenticationPrincipal Jwt principal) {
        String userId = principal != null ? principal.getSubject() : request.userId();
        return OrderResponse.from(orderService.create(request, userId));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('USER')")
    public OrderResponse getById(@PathVariable UUID id) {
        return orderService.findById(id)
            .map(OrderResponse::from)
            .orElseThrow(() -> new NotFoundException("Order " + id));
    }

    @GetMapping
    @PreAuthorize("hasRole('USER')")
    public Page<OrderResponse> list(
            @RequestParam(required = false) OrderStatus status,
            @PageableDefault(size = 20) Pageable pageable) {
        return orderService.find(status, pageable).map(OrderResponse::from);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('MANAGER') or hasRole('ADMIN')")
    public void cancel(@PathVariable UUID id) {
        orderService.cancel(id);
    }
}
