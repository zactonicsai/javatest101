package com.example.tdd.workflow;

import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import io.temporal.client.WorkflowStub;
import io.temporal.testing.TestWorkflowExtension;
import io.temporal.testing.TestWorkflowEnvironment;
import io.temporal.worker.Worker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.InOrder;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class OrderFulfillmentWorkflowTest {

    @RegisterExtension
    static final TestWorkflowExtension testWf = TestWorkflowExtension.newBuilder()
        .setWorkflowTypes(OrderFulfillmentWorkflowImpl.class)
        .setDoNotStart(true)
        .build();

    private FulfillmentActivities activities;

    @BeforeEach
    void registerActivities(TestWorkflowEnvironment env, Worker worker) {
        activities = mock(FulfillmentActivities.class);
        worker.registerActivitiesImplementations(activities);
        env.start();
    }

    @Test
    void fulfil_happyPath_returnsTracking(TestWorkflowEnvironment env,
                                          OrderFulfillmentWorkflow wf) {
        UUID orderId = UUID.randomUUID();
        when(activities.chargePayment(any(), any())).thenReturn(FulfillmentActivities.PaymentResult.ok());
        when(activities.reserveInventory(any())).thenReturn("RES-1");
        when(activities.shipOrder(any(), any())).thenReturn("TRK-9");

        OrderResult result = wf.fulfil(orderId);

        assertThat(result.success()).isTrue();
        assertThat(result.trackingNumber()).isEqualTo("TRK-9");

        InOrder order = inOrder(activities);
        order.verify(activities).chargePayment(eq(orderId), any(BigDecimal.class));
        order.verify(activities).reserveInventory(eq(orderId));
        order.verify(activities).shipOrder(eq(orderId), eq("RES-1"));
        order.verify(activities).notifyCustomer(eq(orderId), contains("TRK-9"));
    }

    @Test
    void fulfil_paymentDeclined_doesNotReserveInventory(
            TestWorkflowEnvironment env, OrderFulfillmentWorkflow wf) {
        when(activities.chargePayment(any(), any()))
            .thenReturn(FulfillmentActivities.PaymentResult.declined("insufficient_funds"));

        OrderResult result = wf.fulfil(UUID.randomUUID());

        assertThat(result.success()).isFalse();
        assertThat(result.failureReason()).contains("payment_declined");
        verify(activities, never()).reserveInventory(any());
        verify(activities, never()).shipOrder(any(), any());
    }

    @Test
    void cancelSignal_triggersInventoryRelease(
            TestWorkflowEnvironment env,
            WorkflowClient client) {
        when(activities.chargePayment(any(), any())).thenReturn(FulfillmentActivities.PaymentResult.ok());
        when(activities.reserveInventory(any())).thenReturn("RES-2");

        UUID orderId = UUID.randomUUID();
        OrderFulfillmentWorkflow wf = client.newWorkflowStub(
            OrderFulfillmentWorkflow.class,
            WorkflowOptions.newBuilder()
                .setTaskQueue("test-queue") // matches the extension's default task queue
                .build());

        WorkflowClient.start(wf::fulfil, orderId);
        env.sleep(Duration.ofSeconds(2));

        wf.cancelRequested();

        OrderResult result = WorkflowStub.fromTyped(wf).getResult(OrderResult.class);
        assertThat(result.cancelled()).isTrue();
        verify(activities).releaseInventory("RES-2");
        verify(activities, never()).shipOrder(any(), any());
    }

    @Test
    void shipOrder_failsTwice_succeedsThirdTime(
            TestWorkflowEnvironment env, OrderFulfillmentWorkflow wf) {
        when(activities.chargePayment(any(), any())).thenReturn(FulfillmentActivities.PaymentResult.ok());
        when(activities.reserveInventory(any())).thenReturn("RES-3");
        when(activities.shipOrder(any(), any()))
            .thenThrow(new RuntimeException("carrier offline"))
            .thenThrow(new RuntimeException("carrier offline"))
            .thenReturn("TRK-FINAL");

        OrderResult result = wf.fulfil(UUID.randomUUID());

        assertThat(result.success()).isTrue();
        assertThat(result.trackingNumber()).isEqualTo("TRK-FINAL");
        verify(activities, times(3)).shipOrder(any(), any());
    }

    private static String contains(String substring) {
        return org.mockito.ArgumentMatchers.contains(substring);
    }
}
