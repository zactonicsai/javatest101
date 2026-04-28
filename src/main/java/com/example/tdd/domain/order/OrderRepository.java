package com.example.tdd.domain.order;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface OrderRepository extends JpaRepository<Order, UUID> {

    Optional<Order> findByIdempotencyKey(String idempotencyKey);

    Optional<Order> findByCorrelationId(String correlationId);

    List<Order> findByUserIdAndStatus(String userId, OrderStatus status);

    Page<Order> findByStatus(OrderStatus status, Pageable pageable);

    @Query("""
        select o from Order o
        where o.status = :status
        and o.createdAt between :from and :to
        """)
    Page<Order> findByStatusInRange(@Param("status") OrderStatus status,
                                    @Param("from") Instant from,
                                    @Param("to") Instant to,
                                    Pageable pageable);

    @Modifying
    @Query("update Order o set o.status = :status where o.id = :id")
    int updateStatus(@Param("id") UUID id, @Param("status") OrderStatus status);
}
