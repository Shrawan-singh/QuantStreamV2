package com.quantstream.backend.messaging;

import com.quantstream.backend.domain.StockTick;
import com.quantstream.backend.processing.TickQueueService;
import com.quantstream.backend.processing.TickValidationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
public class StockTickKafkaConsumer {

    private static final Logger logger = LoggerFactory.getLogger(StockTickKafkaConsumer.class);

    private final TickValidationService tickValidationService;
    private final TickQueueService tickQueueService;

    public StockTickKafkaConsumer(TickValidationService tickValidationService, TickQueueService tickQueueService) {
        this.tickValidationService = tickValidationService;
        this.tickQueueService = tickQueueService;
    }

    @KafkaListener(topics = "${quantstream.streaming.market-ticks-topic}", groupId = "${quantstream.streaming.consumer-group-id}")
    public void onTick(StockTick tick) {
        try {
            tickValidationService.validate(tick);
            tickQueueService.enqueue(tick);
            logger.info("Kafka consumer accepted tick {} for queueing", tick.symbol());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            logger.error("Kafka consumer interrupted while queueing tick {}", tick != null ? tick.symbol() : "null", ex);
            throw new IllegalStateException("Interrupted while queueing tick", ex);
        }
    }
}