package com.example.tdd.messaging;

import io.awspring.cloud.sqs.annotation.SqsListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

@Component
public class OrderEventListener {

    private static final Logger log = LoggerFactory.getLogger(OrderEventListener.class);

    private final OrderProjector projector;

    public OrderEventListener(OrderProjector projector) {
        this.projector = projector;
    }

    @SqsListener("${aws.sqs.order-events-queue}")
    public void onMessage(OrderEvent event,
                          @Header(value = "ApproximateReceiveCount", required = false) Integer receiveCount) {
        if (receiveCount != null && receiveCount > 5) {
            throw new PoisonMessageException(
                "Order event " + event.orderId() + " received " + receiveCount + " times");
        }
        log.info("Received {} for order {} (attempt {})",
            event.type(), event.orderId(), receiveCount);
        projector.apply(event);
    }
}
