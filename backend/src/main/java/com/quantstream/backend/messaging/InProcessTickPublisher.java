/*
 * ==================================================================================
 * FILE: InProcessTickPublisher.java
 * ==================================================================================
 *
 * WHAT THIS FILE DOES:
 * This is the LOCAL DEV publisher.
 *
 * Notice the annotation: `@Profile("local")`.
 * In Spring Boot, profiles let you toggle features based on where the app is running.
 * When you run with `--spring.profiles.active=local`:
 *   - Spring enables THIS class as the active `TickPublisher`.
 *   - Spring DOES NOT connect to Apache Kafka or need Docker.
 *
 * WHAT IT DOES WITH EACH TICK:
 * 1. Checks that the tick is valid (positive price, non-empty symbol, valid timestamp).
 * 2. Enqueues the tick directly into the in-memory `TickQueueService` queue.
 * 3. Our worker pool threads immediately pick it up and process analytics!
 *
 * This provides zero-dependency, ultra-fast local testing on any laptop.
 * ==================================================================================
 */

package com.quantstream.backend.messaging;

import com.quantstream.backend.domain.StockTick;
import com.quantstream.backend.processing.TickQueueService;
import com.quantstream.backend.processing.TickValidationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/**
 * Direct in-process tick publisher for zero-dependency standalone local development.
 */
@Service
@Profile("local")
public class InProcessTickPublisher implements TickPublisher {

    private static final Logger logger = LoggerFactory.getLogger(InProcessTickPublisher.class);

    private final TickValidationService tickValidationService;
    private final TickQueueService tickQueueService;

    public InProcessTickPublisher(TickValidationService tickValidationService, TickQueueService tickQueueService) {
        this.tickValidationService = tickValidationService;
        this.tickQueueService = tickQueueService;
    }

    @Override
    public void publish(StockTick tick) {
        if (tick == null) {
            return;
        }
        try {
            // 1. Sanity check: Ensure price is positive, symbol is valid, etc.
            tickValidationService.validate(tick);

            // 2. Put tick directly into internal in-memory queue
            tickQueueService.enqueue(tick);
            logger.debug("In-process publisher enqueued tick for {}", tick.symbol());
        } catch (InterruptedException ex) {
            // If the application is shutting down while waiting on the queue
            Thread.currentThread().interrupt();
            logger.error("Interrupted while queueing tick for {}", tick.symbol(), ex);
        }
    }
}
