package com.example.tdd.async;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.concurrent.*;

/**
 * Demonstrates Java 21 virtual threads.
 *
 * <p>For Java 21 LTS we use a virtual-thread-per-task executor. The finalized
 * StructuredTaskScope API ships in Java 25 — keeping this implementation
 * straightforward and portable to 21.
 */
@Service
public class FanoutService {

    private static final Logger log = LoggerFactory.getLogger(FanoutService.class);

    private final RestClient restClient;
    private final String baseUrl;

    public FanoutService(RestClient.Builder builder,
                         @org.springframework.beans.factory.annotation.Value("${user-service.base-url}") String baseUrl) {
        this.restClient = builder.baseUrl(baseUrl).build();
        this.baseUrl = baseUrl;
    }

    public List<UserProfile> fetchAll(List<String> userIds) {
        try (ExecutorService scope = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<UserProfile>> futures = userIds.stream()
                .map(id -> scope.submit(() -> fetchOne(id)))
                .toList();

            return futures.stream()
                .map(this::join)
                .toList();
        }
    }

    private UserProfile fetchOne(String id) {
        return restClient.get()
            .uri("/users/{id}", id)
            .retrieve()
            .body(UserProfile.class);
    }

    private UserProfile join(Future<UserProfile> f) {
        try {
            return f.get(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        } catch (ExecutionException | TimeoutException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    public record UserProfile(String id, String email) {}
}
