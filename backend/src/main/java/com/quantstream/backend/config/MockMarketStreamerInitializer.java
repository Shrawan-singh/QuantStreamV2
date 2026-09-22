/*
 * ==================================================================================
 * FILE: MockMarketStreamerInitializer.java
 * ==================================================================================
 *
 * WHAT THIS FILE DOES:
 * This is an `ApplicationRunner`.
 *
 * In Spring Boot, an `ApplicationRunner` is a piece of code that runs AUTOMATICALLY
 * right after the whole server has finished starting up and is ready to work.
 *
 * Here, it simply triggers: `mockMarketStreamer.start()`
 * This automatically turns on the market data stream (either simulation or live Finnhub)
 * without requiring the user to send a manual API command.
 * ==================================================================================
 */

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

    /**
     * Executes immediately upon application startup completion.
     */
    @Override
    public void run(ApplicationArguments args) {
        mockMarketStreamer.start();
    }
}
