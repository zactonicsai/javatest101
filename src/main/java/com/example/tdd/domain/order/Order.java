package com.example.tdd.domain.order;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

@Entity
@Table(name = "orders")
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private OrderStatus status;

    @Column(name = "contact_email")
    private String contactEmail;

    @Column(name = "order_date")
    private LocalDate orderDate;

    @Column(name = "idempotency_key", unique = true)
    private String idempotencyKey;

    @Column(name = "correlation_id", unique = true)
    private String correlationId;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderLine> lines = new ArrayList<>();

    @Column(nullable = false, columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Object> metadata = new HashMap<>();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    protected Order() {}

    public static Order newOrder(String userId, List<OrderLine> lines) {
        Order o = new Order();
        o.userId = userId;
        o.status = OrderStatus.NEW;
        o.attachLines(lines);
        return o;
    }

    public void attachLines(List<OrderLine> newLines) {
        this.lines.clear();
        for (OrderLine line : newLines) {
            line.setOrder(this);
            this.lines.add(line);
        }
    }

    public BigDecimal total() {
        return lines.stream()
            .map(OrderLine::lineTotal)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public void markStatus(OrderStatus next) {
        this.status = next;
    }

    // Accessors
    public UUID getId()                  { return id; }
    public String getUserId()            { return userId; }
    public OrderStatus getStatus()       { return status; }
    public String getContactEmail()      { return contactEmail; }
    public LocalDate getOrderDate()      { return orderDate; }
    public String getIdempotencyKey()    { return idempotencyKey; }
    public String getCorrelationId()     { return correlationId; }
    public List<OrderLine> getLines()    { return List.copyOf(lines); }
    public Map<String, Object> getMetadata() { return metadata; }
    public Instant getCreatedAt()        { return createdAt; }
    public Instant getUpdatedAt()        { return updatedAt; }
    public long getVersion()             { return version; }

    public Order setContactEmail(String e)        { this.contactEmail = e; return this; }
    public Order setOrderDate(LocalDate d)        { this.orderDate = d; return this; }
    public Order setIdempotencyKey(String k)      { this.idempotencyKey = k; return this; }
    public Order setCorrelationId(String c)       { this.correlationId = c; return this; }
    public Order setMetadata(Map<String, Object> m) { this.metadata = m; return this; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Order that)) return false;
        return Objects.equals(id, that.id);
    }

    @Override public int hashCode() { return Objects.hash(id); }
}
