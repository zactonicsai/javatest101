package com.example.tdd.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;

@Service
public class ResilientPaymentClient {

    private static final Logger log = LoggerFactory.getLogger(ResilientPaymentClient.class);

    private final RestClient http;

    public ResilientPaymentClient(RestClient.Builder builder,
                                  @Value("${payment-service.base-url:http://localhost:9998}") String baseUrl) {
        this.http = builder.baseUrl(baseUrl).build();
    }

    @Retryable(
        retryFor = TransientFailure.class,
        maxAttempts = 4,
        backoff = @Backoff(delay = 100, multiplier = 2)
    )
    public PaymentResult charge(ChargeRequest req) {
        try {
            return http.post().uri("/charge")
                .body(req)
                .retrieve()
                .body(PaymentResult.class);
        } catch (ResourceAccessException e) {
            log.warn("Transient failure calling payment service: {}", e.getMessage());
            throw new TransientFailure(e);
        }
    }

    @Recover
    public PaymentResult fallback(TransientFailure e, ChargeRequest req) {
        log.error("Payment service unreachable after retries — deferring {}", req.id());
        return new PaymentResult(req.id(), "DEFERRED", null);
    }

    public record ChargeRequest(String id, BigDecimal amount) {}
    public record PaymentResult(String id, String status, String authCode) {
        public static PaymentResult ok(String id) { return new PaymentResult(id, "OK", "AUTH-" + id); }
        public static PaymentResult deferred(String id) { return new PaymentResult(id, "DEFERRED", null); }
        public static PaymentResult declined(String id, String reason) {
            return new PaymentResult(id, "DECLINED:" + reason, null);
        }
    }

    public static class TransientFailure extends RuntimeException {
        public TransientFailure(Throwable cause) { super(cause); }
    }
}
