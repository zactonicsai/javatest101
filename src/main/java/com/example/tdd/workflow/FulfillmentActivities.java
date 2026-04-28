package com.example.tdd.workflow;

import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;

import java.math.BigDecimal;
import java.util.UUID;

@ActivityInterface
public interface FulfillmentActivities {

    @ActivityMethod
    PaymentResult chargePayment(UUID orderId, BigDecimal amount);

    @ActivityMethod
    String reserveInventory(UUID orderId);

    @ActivityMethod
    String shipOrder(UUID orderId, String reservationId);

    @ActivityMethod
    void notifyCustomer(UUID orderId, String message);

    @ActivityMethod
    void releaseInventory(String reservationId);

    record PaymentResult(boolean success, String authCode, String declineReason) {
        public static PaymentResult ok()                    { return new PaymentResult(true, "AUTH-OK", null); }
        public static PaymentResult declined(String reason) { return new PaymentResult(false, null, reason); }
    }
}
