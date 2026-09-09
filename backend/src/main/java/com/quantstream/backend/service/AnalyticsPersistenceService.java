package com.quantstream.backend.service;

import com.quantstream.backend.domain.dto.AnalyticsSnapshot;
import com.quantstream.backend.domain.entity.AnalyticsSnapshotEntity;
import com.quantstream.backend.repository.AnalyticsSnapshotRepository;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Asynchronous persistence service designed to keep database I/O completely
 * off the high-throughput market data processing hot path.
 *
 * <p>Uses an internal bounded queue and dedicated single-thread worker to throttle
 * and persist analytical snapshot records without blocking processing workers.</p>
 */
@Service
public class AnalyticsPersistenceService {

    private static final Logger logger = LoggerFactory.getLogger(AnalyticsPersistenceService.class);
    private static final int QUEUE_CAPACITY = 2000;
    private static final long PERSIST_THROTTLE_MS = 1500; // At most 1 snapshot per symbol every 1.5s

    private final AnalyticsSnapshotRepository snapshotRepository;

    private final BlockingQueue<AnalyticsSnapshot> persistenceQueue = new LinkedBlockingQueue<>(QUEUE_CAPACITY);
    private final ConcurrentHashMap<String, Long> lastPersistedTime = new ConcurrentHashMap<>();
    private final ExecutorService executor;
    private volatile boolean running = true;

    public AnalyticsPersistenceService(AnalyticsSnapshotRepository snapshotRepository) {
        this.snapshotRepository = snapshotRepository;

        this.executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "analytics-persistence-worker");
            t.setDaemon(true);
            return t;
        });

        this.executor.submit(this::processPersistenceQueue);
    }

    /**
     * Enqueues a snapshot for asynchronous throttled persistence.
     * Non-blocking (uses offer; drops if queue is full during extreme bursts).
     */
    public void enqueue(AnalyticsSnapshot snapshot) {
        if (snapshot == null || snapshot.symbol() == null) {
            return;
        }

        long now = System.currentTimeMillis();
        Long lastTime = lastPersistedTime.get(snapshot.symbol());
        if (lastTime != null && (now - lastTime) < PERSIST_THROTTLE_MS) {
            // Throttled: market state in memory is already updated and broadcast via WebSocket
            return;
        }

        if (persistenceQueue.offer(snapshot)) {
            lastPersistedTime.put(snapshot.symbol(), now);
        } else {
            logger.warn("Persistence queue full, dropping snapshot for symbol {}", snapshot.symbol());
        }
    }

    private void processPersistenceQueue() {
        while (running) {
            try {
                AnalyticsSnapshot snapshot = persistenceQueue.poll(500, TimeUnit.MILLISECONDS);
                if (snapshot != null) {
                    persistSnapshot(snapshot);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                logger.error("Error in background persistence worker: {}", e.getMessage(), e);
            }
        }
    }

    private void persistSnapshot(AnalyticsSnapshot snapshot) {
        try {
            AnalyticsSnapshotEntity entity = new AnalyticsSnapshotEntity(
                    snapshot.symbol(),
                    snapshot.price(),
                    snapshot.sma(),
                    snapshot.ema(),
                    snapshot.rsi(),
                    snapshot.momentum(),
                    snapshot.relativeVolume(),
                    snapshot.convictionScore(),
                    snapshot.scoreCategory() != null ? snapshot.scoreCategory().name() : "NEUTRAL",
                    snapshot.timestamp() != null ? snapshot.timestamp() : Instant.now()
            );
            snapshotRepository.save(entity);
            logger.debug("Persisted snapshot for symbol={}", snapshot.symbol());
        } catch (Exception e) {
            logger.warn("Failed to persist snapshot for symbol {}: {}", snapshot.symbol(), e.getMessage());
        }
    }

    @PreDestroy
    public void shutdown() {
        running = false;
        executor.shutdown();
        try {
            if (!executor.awaitTermination(3, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
