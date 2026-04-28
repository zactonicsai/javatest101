package com.example.tdd.workflow;

import io.temporal.workflow.QueryMethod;
import io.temporal.workflow.SignalMethod;
import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

import java.util.UUID;

@WorkflowInterface
public interface OrderFulfillmentWorkflow {

    @WorkflowMethod
    OrderResult fulfil(UUID orderId);

    @SignalMethod
    void cancelRequested();

    @QueryMethod
    String currentStatus();
}
