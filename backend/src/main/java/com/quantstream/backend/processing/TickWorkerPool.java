/*
 * ==================================================================================
 * FILE: TickWorkerPool.java
 * ==================================================================================
 *
 * WHAT THIS FILE DOES:
 * This is the "FACTORY WORKFORCE" of QuantStream.
 *
 * WHY A WORKER POOL?
 * If you only have one thread in your application, doing heavy math for 50 stocks
 * simultaneously will cause queues to back up and lag.
 * A "Worker Pool" spawns multiple worker threads (e.g. 4, 8, or 16 workers, depending
 * on your CPU cores).
 *
 * HOW THE WORKER LOOP WORKS (`workerLoop()`):
 * Each worker thread runs a continuous while loop:
 *   1. "Is there a tick waiting on the conveyor belt?" -> `tickQueueService.take(...)`
 *   2. If yes: Picks up the tick and runs `tickProcessingService.process(tick)`.
 *   3. If no: Sleeps for a few milliseconds, then checks again.
 *
 * WHAT IS `SmartLifecycle`?
 * In Spring Framework, `SmartLifecycle` gives this class superpowers during startup and shutdown:
 *   - `start()`: Automatically spawns the workers when the server finishes booting up.
 *   - `stop()`: Politely gives workers time to finish their current tick before turning
 *               off the computer, preventing half-finished data corruption.
 * ==================================================================================
 */

package com.quantstream.backend.processing;

import com.quantstream.backend.config.StreamingProperties;
import com.quantstream.backend.domain.StockTick;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;

import jakarta.annotation.PreDestroy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class TickWorkerPool implements SmartLifecycle {

    private static final Logger logger = LoggerFactory.getLogger(TickWorkerPool.class);

    private final StreamingProperties streamingProperties;
    private final TickQueueService tickQueueService;
    private final TickProcessingService tickProcessingService;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final List<Runnable> workerTasks = new ArrayList<>();
    private ExecutorService executorService; // Thread pool manager

    public TickWorkerPool(StreamingProperties streamingProperties,
                          TickQueueService tickQueueService,
                          TickProcessingService tickProcessingService) {
        this.streamingProperties = streamingProperties;
        this.tickQueueService = tickQueueService;
        this.tickProcessingService = tickProcessingService;
        // Prepare worker tasks according to configured pool size (e.g. 4 or 8 threads)
        for (int i = 0; i < streamingProperties.getWorkerPoolSize(); i++) {
            workerTasks.add(this::workerLoop);
        }
    }

    /**
     * Starts the worker pool when Spring boots up.
     */
    @Override
    public synchronized void start() {
        if (running.get()) {
            return;
        }

        executorService = Executors.newFixedThreadPool(streamingProperties.getWorkerPoolSize(), new WorkerThreadFactory());
        workerTasks.forEach(executorService::submit);
        running.set(true);
        logger.info("Tick worker pool started with {} workers", streamingProperties.getWorkerPoolSize());
    }

    /**
     * Gracefully stops the worker threads on shutdown.
     */
    @Override
    public synchronized void stop() {
        if (!running.get()) {
            return;
        }

        running.set(false);
        tickQueueService.shutdown();

        if (executorService != null) {
            executorService.shutdownNow();
            try {
                // Wait up to shutdownTimeoutMs for workers to finish current calculations
                if (!executorService.awaitTermination(streamingProperties.getShutdownTimeoutMs(), TimeUnit.MILLISECONDS)) {
                    logger.warn("Tick worker pool did not terminate within {} ms", streamingProperties.getShutdownTimeoutMs());
                }
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                logger.warn("Interrupted while shutting down tick worker pool", ex);
            }
        }

        logger.info("Tick worker pool stopped");
    }

    @Override
    public void stop(@NonNull Runnable callback) {
        stop();
        callback.run();
    }

    @Override
    public boolean isRunning() {
        return running.get();
    }

    @Override
    public boolean isAutoStartup() {
        return true;
    }

    @Override
    public int getPhase() {
        return Integer.MAX_VALUE - 100;
    }

    @PreDestroy
    public void preDestroy() {
        stop();
    }

    /**
     * The infinite loop executed by each worker thread:
     * Pulls ticks from the queue and executes the processing pipeline.
     */
    private void workerLoop() {
        while (running.get() || tickQueueService.isAccepting()) {
            try {
                // Take next tick off the queue (waits up to poll timeout if queue is empty)
                StockTick tick = tickQueueService.take(streamingProperties.getWorkerPollTimeoutMs());
                if (tick == null) {
                    continue;
                }

                // Process the tick through analytics and broadcasting
                tickProcessingService.process(tick);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                if (!running.get()) {
                    logger.info("Worker {} stopping due to interruption", Thread.currentThread().getName());
                    return;
                }
            } catch (Exception ex) {
                logger.error("Worker {} failed while processing tick", Thread.currentThread().getName(), ex);
            }
        }
    }

    /**
     * Thread factory providing readable thread names: "tick-worker-0", "tick-worker-1", etc.
     */
    private static final class WorkerThreadFactory implements ThreadFactory {

        private int workerIndex = 0;

        @Override
        public synchronized Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "tick-worker-" + workerIndex++);
            thread.setDaemon(false);
            return thread;
        }
    }
}