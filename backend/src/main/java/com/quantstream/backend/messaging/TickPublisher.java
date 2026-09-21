package com.quantstream.backend.messaging;

import com.quantstream.backend.domain.StockTick;

/**
 * Strategy interface for publishing ticks either to Kafka or in-process queue.
 */
@FunctionalInterface
public interface TickPublisher {
    void publish(StockTick tick);
}
