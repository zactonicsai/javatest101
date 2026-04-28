package com.example.tdd.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class OrderProjector {

    private static final Logger log = LoggerFactory.getLogger(OrderProjector.class);

    /** Updates a read-side projection. Replace with real logic in production. */
    public void apply(OrderEvent event) {
        log.debug("Projecting {} for order {}", event.type(), event.orderId());
        // In a real app: update a search index, denormalized view, analytics, etc.
    }
}
