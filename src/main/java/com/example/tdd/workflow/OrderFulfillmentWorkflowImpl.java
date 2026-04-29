package com.example.tdd.workflow;

import io.temporal.activity.ActivityOptions;
import io.temporal.common.RetryOptions;
import io.temporal.workflow.Workflow;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.UUID;

public class OrderFulfillmentWorkflowImpl implements OrderFulfillmentWorkflow {

    private final FulfillmentActivities activities = Workflow.newActivityStub(
        FulfillmentActivities.class,
        ActivityOptions.newBuilder()
            .setStartToCloseTimeout(Duration.ofSeconds(30))
            .setRetryOptions(RetryOptions.newBuilder()
                .setMaximumAttempts(3)
                .setInitialInterval(Duration.ofSeconds(1))
                .setMaximumInterval(Duration.ofSeconds(10))
                .setBackoffCoefficient(2.0)
                .build())
            .build());

    private String status = "NEW";
    private boolean cancelRequested = false;

    @Override
    public OrderResult fulfil(UUID orderId) {
        BigDecimal amount = new BigDecimal("99.99"); // would come from a side-effect lookup

        status = "PAYING";
        FulfillmentActivities.PaymentResult payment = activities.chargePayment(orderId, amount);
        if (!payment.success()) {
            return OrderResult.ofFailure("payment_declined:" + payment.declineReason());
        }

        status = "RESERVING";
        String reservation = activities.reserveInventory(orderId);

        boolean cancelled = Workflow.await(Duration.ofMinutes(5), () -> cancelRequested);
        if (cancelled) {
            activities.releaseInventory(reservation);
            status = "CANCELLED";
            return OrderResult.ofCancellation();
        }

        status = "SHIPPING";
        String tracking = activities.shipOrder(orderId, reservation);

        activities.notifyCustomer(orderId, "Shipped: " + tracking);
        status = "SHIPPED";
        return OrderResult.ofSuccess(tracking);
    }

    @Override public void cancelRequested() { cancelRequested = true; }
    @Override public String currentStatus()  { return status; }
}
