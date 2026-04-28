package com.example.tdd.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.GetQueueUrlRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

@Service
public class OrderEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(OrderEventPublisher.class);

    private final SqsClient sqs;
    private final ObjectMapper mapper;

    @Value("${aws.sqs.order-events-queue}")
    private String queueName;

    public OrderEventPublisher(SqsClient sqs, ObjectMapper mapper) {
        this.sqs = sqs;
        this.mapper = mapper;
    }

    public void publish(OrderEvent event) {
        try {
            String url = sqs.getQueueUrl(GetQueueUrlRequest.builder()
                .queueName(queueName).build()).queueUrl();

            sqs.sendMessage(SendMessageRequest.builder()
                .queueUrl(url)
                .messageBody(mapper.writeValueAsString(event))
                .build());

            log.debug("Published {} for order {}", event.type(), event.orderId());
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize OrderEvent", e);
        }
    }
}
