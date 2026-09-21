package com.quantstream.backend.messaging;

import com.quantstream.backend.domain.StockTick;
import com.quantstream.backend.processing.TickQueueService;
import com.quantstream.backend.processing.TickValidationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/**
 * Direct in-process tick publisher for zero-dependency standalone local development.
 */
@Service
@Profile("local")
public class InProcessTickPublisher implements TickPublisher {

    private static final Logger logger = LoggerFactory.getLogger(InProcessTickPublisher.class);

    private final TickValidationService tickValidationService;
    private final TickQueueService tickQueueService;

    public InProcessTickPublisher(TickValidationService tickValidationService, TickQueueService tickQueueService) {
        this.tickValidationService = tickValidationService;
        this.tickQueueService = tickQueueService;
    }

    @Override
    public void publish(StockTick tick) {
        if (tick == null) {
            return;
        }
        try {
            tickValidationService.validate(tick);
            tickQueueService.enqueue(tick);
            logger.debug("In-process publisher enqueued tick for {}", tick.symbol());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while queueing tick for {}", tick.symbol(), ex);
        }
    }
}
