package com.quantstream.backend.messaging;

import com.quantstream.backend.config.StreamingProperties;
import com.quantstream.backend.domain.StockTick;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;

@Service
@Profile("!local")
public class StockTickKafkaProducer implements TickPublisher {

    private static final Logger logger = LoggerFactory.getLogger(StockTickKafkaProducer.class);

    private final KafkaTemplate<String, StockTick> kafkaTemplate;
    private final StreamingProperties streamingProperties;

    public StockTickKafkaProducer(KafkaTemplate<String, StockTick> kafkaTemplate, StreamingProperties streamingProperties) {
        this.kafkaTemplate = kafkaTemplate;
        this.streamingProperties = streamingProperties;
    }

    public void publish(StockTick tick) {
        String topic = Objects.requireNonNull(streamingProperties.getMarketTicksTopic());
        String key = Objects.requireNonNull(tick.symbol());
        CompletableFuture<?> sendFuture = kafkaTemplate.send(topic, key, tick);
        sendFuture.whenComplete((result, throwable) -> {
            if (throwable != null) {
                logger.error("Failed to publish tick to Kafka topic {}: {}", streamingProperties.getMarketTicksTopic(), tick, throwable);
            } else {
                logger.debug("Published tick to Kafka topic {} with key {}", streamingProperties.getMarketTicksTopic(), tick.symbol());
            }
        });
    }
}