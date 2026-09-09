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
    private ExecutorService executorService;

    public TickWorkerPool(StreamingProperties streamingProperties,
                          TickQueueService tickQueueService,
                          TickProcessingService tickProcessingService) {
        this.streamingProperties = streamingProperties;
        this.tickQueueService = tickQueueService;
        this.tickProcessingService = tickProcessingService;
        for (int i = 0; i < streamingProperties.getWorkerPoolSize(); i++) {
            workerTasks.add(this::workerLoop);
        }
    }

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

    private void workerLoop() {
        while (running.get() || tickQueueService.isAccepting()) {
            try {
                StockTick tick = tickQueueService.take(streamingProperties.getWorkerPollTimeoutMs());
                if (tick == null) {
                    continue;
                }

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