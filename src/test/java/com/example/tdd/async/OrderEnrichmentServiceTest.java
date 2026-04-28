package com.example.tdd.async;

import com.example.tdd.domain.order.Order;
import com.example.tdd.support.OrderFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderEnrichmentServiceTest {

    @Mock OrderEnrichmentService.UserClient userClient;
    @Mock OrderEnrichmentService.InventoryClient inventoryClient;
    @Mock OrderEnrichmentService.PricingClient pricingClient;

    OrderEnrichmentService service;

    @BeforeEach
    void setUp() {
        // Same-thread executor → deterministic, no flakes
        service = new OrderEnrichmentService(
            userClient, inventoryClient, pricingClient, Runnable::run);
    }

    @Test
    void enrich_combinesAllSources() throws Exception {
        Order order = OrderFixtures.simple("USR-1");
        when(userClient.fetch("USR-1"))
            .thenReturn(new OrderEnrichmentService.UserSummary("USR-1", "alice@x"));
        when(inventoryClient.checkAll(any())).thenReturn(Map.of("SKU-1", 10, "SKU-2", 5));
        when(pricingClient.quote(any())).thenReturn(Map.of("SKU-1", new BigDecimal("9.99")));

        CompletableFuture<OrderEnrichmentService.EnrichedOrder> future = service.enrich(order);

        OrderEnrichmentService.EnrichedOrder enriched = future.get(2, TimeUnit.SECONDS);
        assertThat(enriched.success()).isTrue();
        assertThat(enriched.user().email()).isEqualTo("alice@x");
        assertThat(enriched.stock()).containsEntry("SKU-1", 10);
        assertThat(enriched.prices()).containsEntry("SKU-1", new BigDecimal("9.99"));
    }

    @Test
    void enrich_oneSourceFails_returnsFailedOrder() throws Exception {
        Order order = OrderFixtures.simple("USR-1");
        when(userClient.fetch(any())).thenThrow(new RuntimeException("user svc down"));
        when(inventoryClient.checkAll(any())).thenReturn(Map.of());
        when(pricingClient.quote(any())).thenReturn(Map.of());

        OrderEnrichmentService.EnrichedOrder result = service.enrich(order).get(2, TimeUnit.SECONDS);

        assertThat(result.success()).isFalse();
        assertThat(result.failureCause())
            .isNotNull()
            .hasMessageContaining("user svc down");
    }

    @Test
    void enrich_slowSource_triggersTimeout() throws Exception {
        // Use a real executor for this one — we need real wall-clock concurrency
        java.util.concurrent.ExecutorService realExec =
            java.util.concurrent.Executors.newFixedThreadPool(4);
        OrderEnrichmentService realSvc = new OrderEnrichmentService(
            userClient, inventoryClient, pricingClient, realExec);

        try {
            when(userClient.fetch(any())).thenAnswer(inv -> {
                Thread.sleep(8_000);
                return new OrderEnrichmentService.UserSummary("u", "e");
            });
            when(inventoryClient.checkAll(any())).thenReturn(Map.of());
            when(pricingClient.quote(any())).thenReturn(Map.of());

            OrderEnrichmentService.EnrichedOrder result =
                realSvc.enrich(OrderFixtures.simple("USR-1")).get(8, TimeUnit.SECONDS);

            assertThat(result.success()).isFalse();
            assertThat(result.failureCause()).isInstanceOf(TimeoutException.class);
        } finally {
            realExec.shutdownNow();
        }
    }
}
