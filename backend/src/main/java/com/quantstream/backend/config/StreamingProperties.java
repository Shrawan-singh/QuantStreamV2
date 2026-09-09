package com.quantstream.backend.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "quantstream.streaming")
public class StreamingProperties {

    @NotBlank
    private String marketTicksTopic = "market-ticks";

    @NotBlank
    private String consumerGroupId = "quantstream-processing";

    @Min(1)
    private int queueSize = 1000;

    @Min(1)
    private int workerPoolSize = 4;

    @Min(1)
    private long workerPollTimeoutMs = 500L;

    @Min(1)
    private long shutdownTimeoutMs = 5000L;

    public String getMarketTicksTopic() {
        return marketTicksTopic;
    }

    public void setMarketTicksTopic(String marketTicksTopic) {
        this.marketTicksTopic = marketTicksTopic;
    }

    public String getConsumerGroupId() {
        return consumerGroupId;
    }

    public void setConsumerGroupId(String consumerGroupId) {
        this.consumerGroupId = consumerGroupId;
    }

    public int getQueueSize() {
        return queueSize;
    }

    public void setQueueSize(int queueSize) {
        this.queueSize = queueSize;
    }

    public int getWorkerPoolSize() {
        return workerPoolSize;
    }

    public void setWorkerPoolSize(int workerPoolSize) {
        this.workerPoolSize = workerPoolSize;
    }

    public long getWorkerPollTimeoutMs() {
        return workerPollTimeoutMs;
    }

    public void setWorkerPollTimeoutMs(long workerPollTimeoutMs) {
        this.workerPollTimeoutMs = workerPollTimeoutMs;
    }

    public long getShutdownTimeoutMs() {
        return shutdownTimeoutMs;
    }

    public void setShutdownTimeoutMs(long shutdownTimeoutMs) {
        this.shutdownTimeoutMs = shutdownTimeoutMs;
    }
}