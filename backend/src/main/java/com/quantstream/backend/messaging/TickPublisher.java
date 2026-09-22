/*
 * ==================================================================================
 * FILE: TickPublisher.java
 * ==================================================================================
 *
 * WHAT THIS FILE DOES:
 * This is an interface representing a "Post Office" for price ticks.
 *
 * WHY AN INTERFACE IS USED HERE:
 * QuantStream can run in two completely different deployment modes:
 *
 * 1. LOCAL DEVELOPMENT ("local" profile):
 *    No need to install Kafka, Docker, or external cloud message brokers!
 *    `InProcessTickPublisher` directly places the tick into an in-memory queue.
 *
 * 2. CLOUD / ENTERPRISE PRODUCTION ("default" or "docker" profile):
 *    `StockTickKafkaProducer` publishes ticks into an Apache Kafka topic for
 *    distributed microservices at high scale.
 *
 * By declaring this interface:
 *   void publish(StockTick tick);
 * The market data streamers don't need to know or care whether Kafka is running
 * or not. They just call `publish(tick)` and the system routes it appropriately!
 * ==================================================================================
 */

package com.quantstream.backend.messaging;

import com.quantstream.backend.domain.StockTick;

/**
 * Strategy interface for publishing ticks either to Kafka or in-process queue.
 */
@FunctionalInterface
public interface TickPublisher {

    /**
     * Dispatches a market price tick to downstream processing queues or message brokers.
     *
     * @param tick the stock tick to broadcast
     */
    void publish(StockTick tick);
}
