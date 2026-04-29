package com.example.tdd.domain.order;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "order_lines")
public class OrderLine {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    @JsonIgnore // breaks the Order ↔ OrderLine cycle when Jackson serializes for the Redis cache
    private Order order;

    @Column(nullable = false, length = 64)
    private String sku;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "unit_price", nullable = false, precision = 19, scale = 2)
    private BigDecimal unitPrice;

    @Column(name = "line_no", nullable = false)
    private int lineNo;

    protected OrderLine() {}

    public OrderLine(String sku, int quantity, BigDecimal unitPrice, int lineNo) {
        this.sku = sku;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
        this.lineNo = lineNo;
    }

    public BigDecimal lineTotal() {
        return unitPrice.multiply(BigDecimal.valueOf(quantity));
    }

    public UUID getId()                     { return id; }
    public Order getOrder()                 { return order; }
    public String getSku()                  { return sku; }
    public int getQuantity()                { return quantity; }
    public BigDecimal getUnitPrice()        { return unitPrice; }
    public int getLineNo()                  { return lineNo; }

    void setOrder(Order order)              { this.order = order; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof OrderLine that)) return false;
        return Objects.equals(id, that.id);
    }

    @Override public int hashCode() { return Objects.hash(id); }
}
