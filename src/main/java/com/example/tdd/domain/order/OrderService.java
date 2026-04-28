package com.example.tdd.domain.order;

import com.example.tdd.api.dto.CreateOrderRequest;
import com.example.tdd.domain.exception.NotFoundException;
import com.example.tdd.messaging.OrderEvent;
import com.example.tdd.messaging.OrderEventPublisher;
import com.example.tdd.storage.InvoiceStorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@Transactional
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private final OrderRepository repo;
    private final OrderEventPublisher events;
    private final InvoiceStorageService invoices;

    public OrderService(OrderRepository repo,
                        OrderEventPublisher events,
                        InvoiceStorageService invoices) {
        this.repo = repo;
        this.events = events;
        this.invoices = invoices;
    }

    public Order create(CreateOrderRequest request, String authenticatedUserId) {
        // Idempotency check — if a key was provided and we've seen it, return the existing order
        if (request.idempotencyKey() != null) {
            Optional<Order> existing = repo.findByIdempotencyKey(request.idempotencyKey());
            if (existing.isPresent()) {
                log.info("Idempotent replay for key {}", request.idempotencyKey());
                return existing.get();
            }
        }

        List<OrderLine> lines = mapLines(request.items());
        Order order = Order.newOrder(request.userId(), lines)
            .setContactEmail(request.contactEmail())
            .setOrderDate(request.orderDate())
            .setIdempotencyKey(request.idempotencyKey())
            .setCorrelationId(UUID.randomUUID().toString());

        Order saved = repo.save(order);

        // Side effects (post-commit would be safer; kept simple here)
        invoices.storeInvoiceFor(saved);
        events.publish(new OrderEvent(saved.getId(), "ORDER_CREATED",
            saved.getUserId(), saved.total(), Instant.now()));

        return saved;
    }

    @Cacheable(value = "orders", key = "#id", unless = "#result.isEmpty()")
    @Transactional(readOnly = true)
    public Optional<Order> findById(UUID id) {
        return repo.findById(id);
    }

    @Transactional(readOnly = true)
    public Page<Order> find(OrderStatus status, Pageable pageable) {
        if (status != null) return repo.findByStatus(status, pageable);
        return repo.findAll(pageable);
    }

    @CacheEvict(value = "orders", key = "#id")
    public void cancel(UUID id) {
        Order order = repo.findById(id)
            .orElseThrow(() -> new NotFoundException("Order " + id));
        order.markStatus(OrderStatus.CANCELLED);
        repo.save(order);
        events.publish(new OrderEvent(id, "ORDER_CANCELLED",
            order.getUserId(), BigDecimal.ZERO, Instant.now()));
    }

    private static List<OrderLine> mapLines(List<CreateOrderRequest.OrderLine> items) {
        int[] idx = {0};
        return items.stream()
            .map(i -> new OrderLine(i.sku(), i.quantity(), i.unitPrice(), idx[0]++))
            .toList();
    }
}
