package com.example.tdd.workflow;

public record OrderResult(boolean success, boolean cancelled, String trackingNumber, String failureReason) {

    public static OrderResult success(String tracking) {
        return new OrderResult(true, false, tracking, null);
    }
    public static OrderResult failed(String reason) {
        return new OrderResult(false, false, null, reason);
    }
    public static OrderResult cancelled() {
        return new OrderResult(false, true, null, null);
    }
}
