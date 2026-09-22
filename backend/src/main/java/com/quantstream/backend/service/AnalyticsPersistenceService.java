/*
 * ==================================================================================
 * FILE: AnalyticsPersistenceService.java
 * ==================================================================================
 *
 * WHAT THIS FILE DOES:
 * This is the "BACKGROUND CLERK" that writes historical snapshots into the database.
 *
 * WHY NOT SAVE DIRECTLY IN THE MAIN STREAM?
 * Writing to a database (SQL INSERT) involves disk I/O and network latency.
 * It might take 10 to 50 milliseconds.
 * If our stream is processing 1,000 ticks per second, waiting for the database on every
 * single tick would completely choke the pipeline!
 *
 * HOW WE SOLVE THIS (ASYNC BACKGROUND PERSISTENCE):
 * 1. Dedicated Worker Thread:
 *    The main stream worker merely drops the snapshot into an in-memory queue (`persistenceQueue`)
 *    in 0.0001 milliseconds and immediately returns to processing the next price tick!
 * 2. Rate Throttling (`PERSIST_THROTTLE_MS = 1500`):
 *    Even if a stock moves 50 times in one second, humans looking at a chart don't need
 *    50 points for that single second. We throttle database writes to at most 1 snapshot
 *    per stock every 1.5 seconds.
 * 3. Graceful Shedding:
 *    If the database is slow or locked, the queue gently sheds extra snapshots without
 *    ever slowing down the live WebSocket stream to user screens.
 * ==================================================================================
 */

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
 */
@Service
public class AnalyticsPersistenceService {

    private static final Logger logger = LoggerFactory.getLogger(AnalyticsPersistenceService.class);
    private static final int QUEUE_CAPACITY = 2000;
    // Throttle rate: save at most 1 record per stock every 1.5 seconds to save database space
    private static final long PERSIST_THROTTLE_MS = 1500;

    private final AnalyticsSnapshotRepository snapshotRepository;

    // Buffer queue holding snapshots waiting to be written to disk
    private final BlockingQueue<AnalyticsSnapshot> persistenceQueue = new LinkedBlockingQueue<>(QUEUE_CAPACITY);
    // Tracks the last millisecond a symbol was written to the DB
    private final ConcurrentHashMap<String, Long> lastPersistedTime = new ConcurrentHashMap<>();
    private final ExecutorService executor;
    private volatile boolean running = true;

    public AnalyticsPersistenceService(AnalyticsSnapshotRepository snapshotRepository) {
        this.snapshotRepository = snapshotRepository;

        // Dedicated single-thread background worker
        this.executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "analytics-persistence-worker");
            t.setDaemon(true);
            return t;
        });

        // Launch the continuous queue-draining loop
        this.executor.submit(this::processPersistenceQueue);
    }

    /**
     * Called by the main stream worker. Puts the snapshot into the background queue.
     * Non-blocking: returns instantly!
     */
    public void enqueue(AnalyticsSnapshot snapshot) {
        if (snapshot == null || snapshot.symbol() == null) {
            return;
        }

        long now = System.currentTimeMillis();
        Long lastTime = lastPersistedTime.get(snapshot.symbol());
        // If we already saved this symbol less than 1.5 seconds ago, skip saving again
        if (lastTime != null && (now - lastTime) < PERSIST_THROTTLE_MS) {
            return;
        }

        // Put in queue; if queue is overflowing, drops the snapshot rather than locking the server
        if (persistenceQueue.offer(snapshot)) {
            lastPersistedTime.put(snapshot.symbol(), now);
        } else {
            logger.warn("Persistence queue full, dropping snapshot for symbol {}", snapshot.symbol());
        }
    }

    /**
     * Infinite loop executed by the background worker:
     * Takes snapshots off the queue and inserts them into the database.
     */
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

    /**
     * Converts the DTO snapshot into a JPA database entity and saves it.
     */
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
