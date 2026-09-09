package com.quantstream.backend.config;

import com.quantstream.backend.service.MockMarketStreamer;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class MockMarketStreamerInitializer implements ApplicationRunner {

    private final MockMarketStreamer mockMarketStreamer;

    public MockMarketStreamerInitializer(MockMarketStreamer mockMarketStreamer) {
        this.mockMarketStreamer = mockMarketStreamer;
    }

    @Override
    public void run(ApplicationArguments args) {
        mockMarketStreamer.start();
    }
}
