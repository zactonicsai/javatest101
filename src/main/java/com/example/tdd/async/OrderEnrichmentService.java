package com.example.tdd.async;

import com.example.tdd.domain.order.Order;
import com.example.tdd.domain.order.OrderLine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

@Service
public class OrderEnrichmentService {

    private static final Logger log = LoggerFactory.getLogger(OrderEnrichmentService.class);

    private final UserClient userClient;
    private final InventoryClient inventoryClient;
    private final PricingClient pricingClient;
    private final Executor enrichmentExecutor;

    public OrderEnrichmentService(UserClient userClient,
                                  InventoryClient inventoryClient,
                                  PricingClient pricingClient,
                                  @Qualifier("enrichmentExecutor") Executor enrichmentExecutor) {
        this.userClient = userClient;
        this.inventoryClient = inventoryClient;
        this.pricingClient = pricingClient;
        this.enrichmentExecutor = enrichmentExecutor;
    }

    public CompletableFuture<EnrichedOrder> enrich(Order order) {
        var userF = CompletableFuture.supplyAsync(
            () -> userClient.fetch(order.getUserId()), enrichmentExecutor);

        List<String> skus = order.getLines().stream().map(OrderLine::getSku).toList();

        var stockF = CompletableFuture.supplyAsync(
            () -> inventoryClient.checkAll(skus), enrichmentExecutor);

        var priceF = CompletableFuture.supplyAsync(
            () -> pricingClient.quote(skus), enrichmentExecutor);

        return userF
            .thenCombine(stockF, (user, stock) -> new PartialEnrichment(user, stock))
            .thenCombine(priceF, (partial, prices) ->
                new EnrichedOrder(order, partial.user(), partial.stock(), prices, true, null))
            .orTimeout(5, TimeUnit.SECONDS)
            .exceptionally(ex -> {
                log.warn("Enrichment failed for order {}: {}", order.getId(), ex.getMessage());
                Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                return new EnrichedOrder(order, null, Map.of(), Map.of(), false, cause);
            });
    }

    private record PartialEnrichment(UserSummary user, Map<String, Integer> stock) {}

    public record UserSummary(String id, String email) {}

    public record EnrichedOrder(
        Order order,
        UserSummary user,
        Map<String, Integer> stock,
        Map<String, BigDecimal> prices,
        boolean success,
        Throwable failureCause
    ) {}

    public interface UserClient      { UserSummary fetch(String userId); }
    public interface InventoryClient { Map<String, Integer> checkAll(List<String> skus); }
    public interface PricingClient   { Map<String, BigDecimal> quote(List<String> skus); }
}
