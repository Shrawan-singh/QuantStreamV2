/*
 * ==================================================================================
 * FILE: StockTickKafkaConsumer.java
 * ==================================================================================
 *
 * WHAT THIS FILE DOES:
 * This is the counterpart to `StockTickKafkaProducer` in production mode.
 *
 * Notice the annotation: `@Profile("!local")`.
 * When running with Kafka:
 *   - The producer pushed ticks into the "market-ticks" topic.
 *   - THIS class listens to that topic (`@KafkaListener`).
 *
 * WHAT IT DOES WHEN A TICK ARRIVES FROM KAFKA:
 * 1. `@KafkaListener(...)`: Spring Kafka automatically wakes this method up whenever
 *    a new tick message is received from the cluster.
 * 2. `tickValidationService.validate(tick)`: Verifies data integrity (no negative prices, etc.).
 * 3. `tickQueueService.enqueue(tick)`: Hands the tick over to our internal worker queue
 *    so our high-speed analytics thread pool can process it.
 * ==================================================================================
 */

package com.quantstream.backend.messaging;

import com.quantstream.backend.domain.StockTick;
import com.quantstream.backend.processing.TickQueueService;
import com.quantstream.backend.processing.TickValidationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
@Profile("!local")
public class StockTickKafkaConsumer {

    private static final Logger logger = LoggerFactory.getLogger(StockTickKafkaConsumer.class);

    private final TickValidationService tickValidationService;
    private final TickQueueService tickQueueService;

    public StockTickKafkaConsumer(TickValidationService tickValidationService, TickQueueService tickQueueService) {
        this.tickValidationService = tickValidationService;
        this.tickQueueService = tickQueueService;
    }

    /**
     * Listens for ticks published onto the Kafka market-ticks topic.
     */
    @KafkaListener(topics = "${quantstream.streaming.market-ticks-topic}", groupId = "${quantstream.streaming.consumer-group-id}")
    public void onTick(StockTick tick) {
        try {
            // 1. Validate incoming tick data
            tickValidationService.validate(tick);
            // 2. Put tick into our internal worker queue
            tickQueueService.enqueue(tick);
            logger.debug("Kafka consumer accepted tick {} for queueing", tick.symbol());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            logger.error("Kafka consumer interrupted while queueing tick {}", tick != null ? tick.symbol() : "null", ex);
            throw new IllegalStateException("Interrupted while queueing tick", ex);
        }
    }
}