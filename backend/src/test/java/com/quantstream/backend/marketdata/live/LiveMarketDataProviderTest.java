package com.quantstream.backend.marketdata.live;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.quantstream.backend.domain.StockTick;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SuppressWarnings("null")
class LiveMarketDataProviderTest {

    private FinnhubTradeParser parser;
    private LiveMarketDataProvider provider;

    @BeforeEach
    void setUp() {
        parser = new FinnhubTradeParser(new ObjectMapper());
        provider = new LiveMarketDataProvider(parser);
    }

    @AfterEach
    void tearDown() {
        if (provider != null) {
            provider.shutdown();
        }
    }

    @Test
    @DisplayName("When API key is empty, healthStatus is UNCONFIGURED and connect is a safe no-op")
    void unconfiguredWhenApiKeyMissing() {
        ReflectionTestUtils.setField(provider, "apiKey", "");
        ReflectionTestUtils.setField(provider, "websocketUrl", "wss://ws.finnhub.io");
        ReflectionTestUtils.setField(provider, "providerName", "finnhub");

        assertThat(provider.healthStatus()).isEqualTo("UNCONFIGURED");
        provider.connect();
        assertThat(provider.healthStatus()).isEqualTo("UNCONFIGURED");
    }

    @Test
    @DisplayName("Symbols can be subscribed and unsubscribed cleanly")
    void subscribeAndUnsubscribe() {
        provider.subscribe("aapl");
        provider.subscribe("MSFT");
        provider.subscribe("googl");

        assertThat(provider.getSubscribedSymbols())
                .containsExactlyInAnyOrder("AAPL", "MSFT", "GOOGL");

        provider.unsubscribe("msft");
        assertThat(provider.getSubscribedSymbols())
                .containsExactlyInAnyOrder("AAPL", "GOOGL");

        // Null and blank safety
        provider.subscribe(null);
        provider.subscribe("   ");
        assertThat(provider.getSubscribedSymbols()).hasSize(2);
    }

    @Test
    @DisplayName("Connection failure triggers reconnect schedule and backs off gracefully")
    void connectionFailureSchedulesReconnectWithBackoff() {
        ReflectionTestUtils.setField(provider, "apiKey", "dummy_token");
        // Point to an invalid local port that refuses connection immediately
        ReflectionTestUtils.setField(provider, "websocketUrl", "ws://127.0.0.1:54321");
        ReflectionTestUtils.setField(provider, "providerName", "finnhub");

        provider.connect();

        // Immediately health status should be DISCONNECTED since connection failed
        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            assertThat(provider.healthStatus()).isEqualTo("DISCONNECTED");
        });

        // The backoff should have advanced from initial 2s to 4s
        Integer backoff = (Integer) ReflectionTestUtils.getField(provider, "retryBackoffSeconds");
        assertThat(backoff).isNotNull().isGreaterThanOrEqualTo(4);

        AtomicBoolean active = (AtomicBoolean) ReflectionTestUtils.getField(provider, "active");
        assertThat(active).isNotNull().isTrue();

        // Disconnecting deactivates reconnect loop
        provider.disconnect();
        assertThat(java.util.Objects.requireNonNull(active).get()).isFalse();
    }

    @Test
    @DisplayName("Tick listener can be registered and disconnect shuts down session cleanly")
    void tickListenerAndDisconnect() {
        List<StockTick> received = new ArrayList<>();
        provider.setTickListener(received::add);

        provider.disconnect();
        assertThat(provider.healthStatus()).isEqualTo("UNCONFIGURED");
    }
}
