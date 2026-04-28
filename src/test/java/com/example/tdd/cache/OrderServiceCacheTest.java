package com.example.tdd.cache;

import com.example.tdd.domain.order.Order;
import com.example.tdd.domain.order.OrderRepository;
import com.example.tdd.domain.order.OrderService;
import com.example.tdd.support.OrderFixtures;
import com.redis.testcontainers.RedisContainer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class OrderServiceCacheTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres =
        new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    @ServiceConnection
    static final RedisContainer redis =
        new RedisContainer(DockerImageName.parse("redis:7-alpine"));

    @Autowired OrderService orderService;
    @Autowired CacheManager cacheManager;

    // OrderRepository is mocked so we can count the underlying call
    @MockitoBean OrderRepository repo;

    @MockitoBean com.example.tdd.messaging.OrderEventPublisher events;
    @MockitoBean com.example.tdd.storage.InvoiceStorageService invoices;

    @BeforeEach
    void clearCache() {
        Objects.requireNonNull(cacheManager.getCache("orders")).clear();
    }

    @AfterEach
    void resetMocks() { reset(repo); }

    @Test
    void firstCall_hitsDb_secondCall_hitsCache() {
        UUID id = UUID.randomUUID();
        Order order = OrderFixtures.simple("USR-1");
        when(repo.findById(id)).thenReturn(Optional.of(order));

        orderService.findById(id);
        orderService.findById(id);
        orderService.findById(id);

        verify(repo, times(1)).findById(id);
    }

    @Test
    void cancel_evictsCachedEntry() {
        UUID id = UUID.randomUUID();
        Order order = OrderFixtures.simple("USR-1");
        when(repo.findById(id)).thenReturn(Optional.of(order));

        orderService.findById(id);                    // populates cache
        orderService.cancel(id);                      // evicts
        orderService.findById(id);                    // fresh DB hit

        // findById(id) called once before, plus once inside cancel(), plus once after eviction = 3
        verify(repo, times(3)).findById(id);
    }

    @Test
    void emptyOptional_isNotCached() {
        UUID id = UUID.randomUUID();
        when(repo.findById(id)).thenReturn(Optional.empty());

        orderService.findById(id);
        orderService.findById(id);

        // Cache returns empty results too, so the underlying repo is hit once.
        // If your config disables null caching, both calls hit the DB.
        Cache cache = cacheManager.getCache("orders");
        assertThat(cache.get(id)).isNull();
    }
}
