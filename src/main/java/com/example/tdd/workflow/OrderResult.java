package com.example.tdd.workflow;

public record OrderResult(boolean success, boolean cancelled, String trackingNumber, String failureReason) {

    public static OrderResult ofSuccess(String tracking) {
        return new OrderResult(true, false, tracking, null);
    }
    public static OrderResult ofFailure(String reason) {
        return new OrderResult(false, false, null, reason);
    }
    public static OrderResult ofCancellation() {
        return new OrderResult(false, true, null, null);
    }
}
