/*
 * ==================================================================================
 * FILE: StockTickKafkaProducer.java
 * ==================================================================================
 *
 * WHAT THIS FILE DOES:
 * This is the PRODUCTION message producer for Apache Kafka.
 *
 * Notice the annotation: `@Profile("!local")` (which means "NOT local").
 * When deploying into a real Docker or Kubernetes cluster with Kafka, this class
 * activates instead of `InProcessTickPublisher`.
 *
 * WHAT IS APACHE KAFKA?
 * Apache Kafka is an ultra-fast, distributed streaming log used by companies like
 * Netflix, Uber, and Wall Street banks. It can handle millions of messages per second.
 *
 * HOW IT WORKS HERE:
 * 1. Takes the `StockTick` record.
 * 2. Uses the stock symbol (e.g. "AAPL") as the Kafka "Partition Key".
 *    Why? Because in Kafka, all messages with the same key go to the SAME partition.
 *    This guarantees that price ticks for Apple always arrive in strict chronological order!
 * 3. Sends the message asynchronously to the configured Kafka topic (e.g. "market-ticks").
 * 4. Logs success or failure when the Kafka broker acknowledges the message.
 * ==================================================================================
 */

package com.quantstream.backend.messaging;

import com.quantstream.backend.config.StreamingProperties;
import com.quantstream.backend.domain.StockTick;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;

@Service
@Profile("!local")
public class StockTickKafkaProducer implements TickPublisher {

    private static final Logger logger = LoggerFactory.getLogger(StockTickKafkaProducer.class);

    // Spring Kafka helper for sending messages
    private final KafkaTemplate<String, StockTick> kafkaTemplate;
    private final StreamingProperties streamingProperties;

    public StockTickKafkaProducer(KafkaTemplate<String, StockTick> kafkaTemplate, StreamingProperties streamingProperties) {
        this.kafkaTemplate = kafkaTemplate;
        this.streamingProperties = streamingProperties;
    }

    /**
     * Publishes a tick to the distributed Kafka cluster.
     */
    public void publish(StockTick tick) {
        String topic = Objects.requireNonNull(streamingProperties.getMarketTicksTopic());
        // Using stock symbol as partition key ensures strict ordering per symbol
        String key = Objects.requireNonNull(tick.symbol());

        // Asynchronously dispatch to Kafka
        CompletableFuture<?> sendFuture = kafkaTemplate.send(topic, key, tick);
        sendFuture.whenComplete((result, throwable) -> {
            if (throwable != null) {
                logger.error("Failed to publish tick to Kafka topic {}: {}", streamingProperties.getMarketTicksTopic(), tick, throwable);
            } else {
                logger.debug("Published tick to Kafka topic {} with key {}", streamingProperties.getMarketTicksTopic(), tick.symbol());
            }
        });
    }
}