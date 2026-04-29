package com.example.tdd.config;

import com.example.tdd.workflow.FulfillmentActivities;
import com.example.tdd.workflow.OrderFulfillmentWorkflowImpl;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowClientOptions;
import io.temporal.serviceclient.WorkflowServiceStubs;
import io.temporal.serviceclient.WorkflowServiceStubsOptions;
import io.temporal.worker.Worker;
import io.temporal.worker.WorkerFactory;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires up the Temporal SDK against the docker-compose Temporal server.
 *
 * <p>Disabled by default ({@code temporal.enabled=false} in application.yml).
 * Set {@code temporal.enabled=true} to start a worker on app boot. Workflow
 * unit tests don't need this — they use {@link io.temporal.testing.TestWorkflowExtension}
 * for an in-process server.
 *
 * <h3>Lifecycle</h3>
 * <ol>
 *   <li>{@link WorkflowServiceStubs} opens a long-lived gRPC channel to {@code temporal:7233}.</li>
 *   <li>{@link WorkflowClient} is the API surface for starting / signalling workflows.</li>
 *   <li>{@link WorkerFactory} hosts one or more workers; each polls a single task queue.</li>
 *   <li>The worker registers the workflow IMPL class and the activity IMPL bean.</li>
 *   <li>{@link WorkerFactory#start()} kicks off polling threads.</li>
 * </ol>
 */
@Configuration
@ConditionalOnProperty(prefix = "temporal", name = "enabled", havingValue = "true")
public class TemporalConfig {

    private static final Logger log = LoggerFactory.getLogger(TemporalConfig.class);

    @Value("${temporal.service-address:localhost:7233}")
    private String serviceAddress;

    @Value("${temporal.namespace:default}")
    private String namespace;

    @Value("${temporal.task-queue:ORDER_TASK_QUEUE}")
    private String taskQueue;

    private final FulfillmentActivities activities;

    private WorkerFactory factory;

    public TemporalConfig(FulfillmentActivities activities) {
        this.activities = activities;
    }

    @Bean(destroyMethod = "shutdownNow")
    public WorkflowServiceStubs workflowServiceStubs() {
        log.info("Connecting Temporal service at {}", serviceAddress);
        return WorkflowServiceStubs.newServiceStubs(
            WorkflowServiceStubsOptions.newBuilder()
                .setTarget(serviceAddress)
                .build());
    }

    @Bean
    public WorkflowClient workflowClient(WorkflowServiceStubs stubs) {
        return WorkflowClient.newInstance(stubs,
            WorkflowClientOptions.newBuilder()
                .setNamespace(namespace)
                .build());
    }

    @Bean
    public WorkerFactory workerFactory(WorkflowClient client) {
        this.factory = WorkerFactory.newInstance(client);
        Worker worker = factory.newWorker(taskQueue);
        worker.registerWorkflowImplementationTypes(OrderFulfillmentWorkflowImpl.class);
        worker.registerActivitiesImplementations(activities);
        return factory;
    }

    @PostConstruct
    void startWorker() {
        // This runs after all @Bean methods complete; factory is non-null.
        if (factory != null) {
            factory.start();
            log.info("Temporal worker polling task queue '{}' on namespace '{}'",
                taskQueue, namespace);
        }
    }

    @PreDestroy
    void stopWorker() {
        if (factory != null) {
            log.info("Shutting down Temporal worker factory");
            factory.shutdown();
        }
    }
}
