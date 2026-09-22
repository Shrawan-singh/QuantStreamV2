/*
 * ==================================================================================
 * FILE: TickQueueService.java
 * ==================================================================================
 *
 * WHAT THIS FILE DOES:
 * This is the "SHOCK ABSORBER" (Buffer Queue) between incoming market data and
 * our analytics processing engine.
 *
 * REAL WORLD ANALOGY:
 * Imagine a conveyor belt at a busy airport luggage counter.
 * Suitcases (stock ticks) come flying in fast. The luggage handlers (worker threads)
 * pick suitcases off the conveyor belt to inspect them.
 *
 * WHY A BOUNDED QUEUE (ArrayBlockingQueue)?
 * If 100,000 ticks arrive in 2 seconds, and we store an infinite number of them in memory,
 * the computer will eventually run out of RAM and crash (OutOfMemoryError).
 * Instead, we set a maximum capacity (e.g., 50,000 items).
 *
 * WHAT IS "BACKPRESSURE"?
 * If the queue gets completely full, `queue.offer(tick, 1, TimeUnit.SECONDS)` gently
 * pauses the producer thread for up to 1 second, giving our worker threads a chance
 * to catch up and drain items from the conveyor belt!
 * ==================================================================================
 */

package com.quantstream.backend.processing;

import com.quantstream.backend.config.StreamingProperties;
import com.quantstream.backend.domain.StockTick;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import jakarta.annotation.PreDestroy;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class TickQueueService {

    private static final Logger logger = LoggerFactory.getLogger(TickQueueService.class);

    private final StreamingProperties streamingProperties;

    // Bounded thread-safe queue: Producer threads put ticks in; Worker threads take ticks out
    private final ArrayBlockingQueue<StockTick> queue;

    // Flag tracking if the queue is open and accepting new ticks
    private final AtomicBoolean accepting = new AtomicBoolean(true);

    public TickQueueService(StreamingProperties streamingProperties) {
        this.streamingProperties = streamingProperties;
        // Allocate bounded queue with configured maximum size (e.g. 50,000)
        this.queue = new ArrayBlockingQueue<>(streamingProperties.getQueueSize());
    }

    /**
     * Places a tick into the queue.
     * If the queue is currently full, blocks and waits up to 1 second for space (backpressure).
     */
    public void enqueue(StockTick tick) throws InterruptedException {
        if (!accepting.get()) {
            throw new IllegalStateException("Tick queue is shutting down");
        }

        while (accepting.get()) {
            // Try to place tick on queue; wait up to 1 second if full
            if (queue.offer(tick, 1, TimeUnit.SECONDS)) {
                logger.debug("Queued tick {}. queueSize={}", tick.symbol(), queue.size());
                return;
            }

            // Queue is full! Warn in logs while applying backpressure
            logger.warn("Tick queue full. Applying backpressure. queueSize={}/{}", queue.size(), streamingProperties.getQueueSize());
        }

        throw new IllegalStateException("Tick queue stopped before enqueue completed");
    }

    /**
     * Used by background worker threads to take the next tick off the conveyor belt.
     * Waits up to timeoutMs if the queue is temporarily empty.
     */
    public StockTick take(long timeoutMs) throws InterruptedException {
        return queue.poll(timeoutMs, TimeUnit.MILLISECONDS);
    }

    /** Current number of ticks waiting on the conveyor belt */
    public int size() {
        return queue.size();
    }

    /** Maximum capacity of the queue */
    public int capacity() {
        return streamingProperties.getQueueSize();
    }

    public boolean isAccepting() {
        return accepting.get();
    }

    /** Stops accepting new ticks during graceful application shutdown */
    public void shutdown() {
        accepting.set(false);
    }

    @PreDestroy
    public void preDestroy() {
        shutdown();
    }
}