package com.example.tdd.messaging;

import com.example.tdd.config.AwsConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.containers.localstack.LocalStackContainer.Service;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.GetQueueUrlRequest;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    classes = {AwsConfig.class, OrderEventPublisher.class, JacksonAutoConfiguration.class}
)
@ActiveProfiles("test")
@Testcontainers
class OrderEventPublisherTest {

    @Container
    static final LocalStackContainer localStack = new LocalStackContainer(
            DockerImageName.parse("localstack/localstack:3.8"))
        .withServices(Service.SQS);

    @DynamicPropertySource
    static void awsProps(DynamicPropertyRegistry r) {
        r.add("aws.endpoint",   () -> localStack.getEndpoint().toString());
        r.add("aws.region",     localStack::getRegion);
        r.add("aws.access-key", localStack::getAccessKey);
        r.add("aws.secret-key", localStack::getSecretKey);
        r.add("aws.sqs.order-events-queue", () -> "order-events");
    }

    @BeforeAll
    static void createQueue() throws Exception {
        localStack.execInContainer("awslocal", "sqs", "create-queue",
            "--queue-name", "order-events");
    }

    @Autowired OrderEventPublisher publisher;
    @Autowired SqsClient sqsClient;
    @Autowired ObjectMapper mapper;

    @Test
    void publish_messageLandsOnQueue() throws Exception {
        OrderEvent event = new OrderEvent(
            UUID.randomUUID(), "ORDER_CREATED",
            "USR-1", new BigDecimal("99.99"), Instant.now());

        publisher.publish(event);

        String url = sqsClient.getQueueUrl(GetQueueUrlRequest.builder()
            .queueName("order-events").build()).queueUrl();

        List<Message> msgs = sqsClient.receiveMessage(ReceiveMessageRequest.builder()
            .queueUrl(url)
            .waitTimeSeconds(2)
            .maxNumberOfMessages(1)
            .build()).messages();

        assertThat(msgs).hasSize(1);
        OrderEvent received = mapper.readValue(msgs.get(0).body(), OrderEvent.class);
        assertThat(received.orderId()).isEqualTo(event.orderId());
        assertThat(received.type()).isEqualTo("ORDER_CREATED");
    }
}
