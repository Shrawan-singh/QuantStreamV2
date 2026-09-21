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
    private final ArrayBlockingQueue<StockTick> queue;
    private final AtomicBoolean accepting = new AtomicBoolean(true);

    public TickQueueService(StreamingProperties streamingProperties) {
        this.streamingProperties = streamingProperties;
        this.queue = new ArrayBlockingQueue<>(streamingProperties.getQueueSize());
    }

    public void enqueue(StockTick tick) throws InterruptedException {
        if (!accepting.get()) {
            throw new IllegalStateException("Tick queue is shutting down");
        }

        while (accepting.get()) {
            if (queue.offer(tick, 1, TimeUnit.SECONDS)) {
                logger.debug("Queued tick {}. queueSize={}", tick.symbol(), queue.size());
                return;
            }

            logger.warn("Tick queue full. Applying backpressure. queueSize={}/{}", queue.size(), streamingProperties.getQueueSize());
        }

        throw new IllegalStateException("Tick queue stopped before enqueue completed");
    }

    public StockTick take(long timeoutMs) throws InterruptedException {
        return queue.poll(timeoutMs, TimeUnit.MILLISECONDS);
    }

    public int size() {
        return queue.size();
    }

    public int capacity() {
        return streamingProperties.getQueueSize();
    }

    public boolean isAccepting() {
        return accepting.get();
    }

    public void shutdown() {
        accepting.set(false);
    }

    @PreDestroy
    public void preDestroy() {
        shutdown();
    }
}