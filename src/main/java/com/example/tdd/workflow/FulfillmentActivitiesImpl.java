package com.example.tdd.workflow;

import io.temporal.activity.Activity;
import io.temporal.activity.ActivityExecutionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Real-side-effect activity implementation.
 *
 * <p>Activities are the only place a workflow may touch the outside world —
 * databases, HTTP, queues. They are NOT replayed on worker restart, so they
 * may use random IDs, current time, network calls, etc. They MUST be
 * idempotent because Temporal will retry them on failure or worker death.
 *
 * <p>The methods here are stubbed for the demo; replace with real calls into
 * payment gateways, inventory APIs, shipping providers, etc.
 */
@Component
public class FulfillmentActivitiesImpl implements FulfillmentActivities {

    private static final Logger log = LoggerFactory.getLogger(FulfillmentActivitiesImpl.class);

    @Override
    public PaymentResult chargePayment(UUID orderId, BigDecimal amount) {
        ActivityExecutionContext ctx = Activity.getExecutionContext();
        log.info("Charging {} for order {} (attempt {})",
            amount, orderId, ctx.getInfo().getAttempt());

        // In a real impl: call payment gateway and translate the response.
        // Throwing a RuntimeException here triggers the workflow's RetryOptions.
        return PaymentResult.ok();
    }

    @Override
    public String reserveInventory(UUID orderId) {
        log.info("Reserving inventory for order {}", orderId);
        return "RES-" + orderId.toString().substring(0, 8);
    }

    @Override
    public String shipOrder(UUID orderId, String reservationId) {
        log.info("Shipping order {} (reservation {})", orderId, reservationId);
        return "TRK-" + orderId.toString().substring(0, 8);
    }

    @Override
    public void notifyCustomer(UUID orderId, String message) {
        log.info("Notifying customer about order {}: {}", orderId, message);
    }

    @Override
    public void releaseInventory(String reservationId) {
        log.info("Releasing inventory reservation {}", reservationId);
    }
}
