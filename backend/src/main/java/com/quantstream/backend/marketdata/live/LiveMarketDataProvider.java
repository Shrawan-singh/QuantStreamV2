/*
 * ==================================================================================
 * FILE: LiveMarketDataProvider.java
 * ==================================================================================
 *
 * WHAT THIS FILE DOES:
 * This is the REAL-TIME LIVE MARKET pipeline connecting QuantStream to Wall Street!
 *
 * WHEN IS THIS USED?
 * Only when QuantStream is launched in "live" mode:
 *   quantstream.marketdata.mode=live
 *
 * HOW WE CONNECT TO WALL STREET (Finnhub WebSocket):
 * 1. WebSockets vs Regular HTTP:
 *    Normally, a website only gets data when you refresh the page (HTTP request).
 *    A "WebSocket" is a permanent two-way telephone call! Once opened, Finnhub's
 *    servers push new stock trades to us the instant they happen in New York.
 *
 * 2. Safe Graceful Handling (No API Key):
 *    If the user has not set their `FINNHUB_API_KEY` environment variable,
 *    this class does NOT crash the app. Instead, it reports its status as "UNCONFIGURED"
 *    and prints a friendly explanation in the logs.
 *
 * 3. Subscribing to Stocks:
 *    When we connect, we send a message for each stock:
 *      {"type":"subscribe", "symbol":"AAPL"}
 *    Finnhub will then start sending us AAPL trades immediately.
 *
 * 4. Automatic Reconnection (Exponential Backoff):
 *    Internet connections drop sometimes. If the Wi-Fi blips or Finnhub reboots,
 *    this class automatically tries to reconnect: first waiting 2 seconds, then 4,
 *    then 8, up to 30 seconds, until the connection is restored!
 * ==================================================================================
 */

package com.quantstream.backend.marketdata.live;

import com.quantstream.backend.domain.StockTick;
import com.quantstream.backend.marketdata.MarketDataProvider;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Real-time live market data provider using the official Finnhub WebSocket trade streaming API.
 */
@Service
@ConditionalOnProperty(name = "quantstream.marketdata.mode", havingValue = "live")
public class LiveMarketDataProvider implements MarketDataProvider {

    private static final Logger logger = LoggerFactory.getLogger(LiveMarketDataProvider.class);

    private final FinnhubTradeParser tradeParser;

    @Value("${quantstream.marketdata.provider:FINNHUB}")
    private String providerName;

    // Read API key from application.yml or environment variable FINNHUB_API_KEY
    @Value("${quantstream.marketdata.api-key:${FINNHUB_API_KEY:}}")
    private String apiKey;

    @Value("${quantstream.marketdata.websocket-url:${FINNHUB_WEBSOCKET_URL:wss://ws.finnhub.io}}")
    private String websocketUrl;

    private final AtomicBoolean connected = new AtomicBoolean(false);
    private final AtomicBoolean active = new AtomicBoolean(false);

    // Set of US stock symbols currently subscribed to (e.g. AAPL, MSFT, NVDA)
    private final Set<String> subscribedSymbols = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private volatile Consumer<StockTick> tickListener = tick -> {};

    private HttpClient httpClient;
    private WebSocket webSocket;

    // Background timer thread for scheduling reconnection attempts
    private final ScheduledExecutorService reconnectScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "finnhub-reconnect-worker");
        t.setDaemon(true);
        return t;
    });

    // Backoff delay in seconds: doubles on failure (2s -> 4s -> 8s -> 16s -> 30s)
    private volatile int retryBackoffSeconds = 2;

    public LiveMarketDataProvider(FinnhubTradeParser tradeParser) {
        this.tradeParser = tradeParser;
    }

    /**
     * Connects to Finnhub's live WebSocket server.
     */
    @Override
    public synchronized void connect() {
        if (apiKey == null || apiKey.isBlank()) {
            logger.warn("Live market data provider '{}' cannot connect: FINNHUB_API_KEY is not configured. " +
                    "Status is UNCONFIGURED. Set FINNHUB_API_KEY in environment to stream live US market ticks.", providerName);
            return;
        }

        active.set(true);

        if (connected.get()) {
            return; // Already connected
        }

        try {
            // Append API token to WebSocket URL
            String fullUrl = websocketUrl.trim();
            if (!fullUrl.contains("token=")) {
                fullUrl = fullUrl + (fullUrl.contains("?") ? "&" : "?") + "token=" + apiKey.trim();
            }

            URI uri = URI.create(fullUrl);
            logger.info("Connecting to Finnhub live WebSocket at {} (configured for {} symbols)...",
                    websocketUrl, subscribedSymbols.size());

            if (httpClient == null) {
                httpClient = HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(10))
                        .build();
            }

            // Asynchronously establish the WebSocket connection
            httpClient.newWebSocketBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .buildAsync(uri, new FinnhubWebSocketListener())
                    .thenAccept(ws -> {
                        this.webSocket = ws;
                        this.connected.set(true);
                        this.retryBackoffSeconds = 2; // Reset retry timer on successful connection
                        logger.info("Successfully connected to Finnhub live WebSocket. Subscribing to symbols: {}", subscribedSymbols);
                        for (String symbol : subscribedSymbols) {
                            sendSubscribe(ws, symbol);
                        }
                    })
                    .exceptionally(ex -> {
                        this.connected.set(false);
                        logger.warn("Failed to connect to Finnhub live WebSocket: {}. Scheduling retry...", ex.getMessage());
                        scheduleReconnect();
                        return null;
                    });
        } catch (Exception e) {
            this.connected.set(false);
            logger.error("Exception initiating Finnhub live WebSocket connection: {}", e.getMessage(), e);
            scheduleReconnect();
        }
    }

    /**
     * Subscribes to a stock ticker symbol (e.g. "AAPL").
     */
    @Override
    public void subscribe(String symbol) {
        if (symbol == null || symbol.isBlank()) return;
        String upperSym = symbol.trim().toUpperCase();
        subscribedSymbols.add(upperSym);

        WebSocket ws = this.webSocket;
        if (ws != null && connected.get()) {
            sendSubscribe(ws, upperSym);
        }
        logger.info("Registered subscription for symbol {} on Finnhub provider", upperSym);
    }

    /**
     * Unsubscribes from a stock ticker symbol.
     */
    @Override
    public void unsubscribe(String symbol) {
        if (symbol == null || symbol.isBlank()) return;
        String upperSym = symbol.trim().toUpperCase();
        subscribedSymbols.remove(upperSym);

        WebSocket ws = this.webSocket;
        if (ws != null && connected.get()) {
            sendUnsubscribe(ws, upperSym);
        }
        logger.info("Unsubscribed from symbol {} on Finnhub provider", upperSym);
    }

    /**
     * Gracefully closes the WebSocket connection.
     */
    @Override
    public synchronized void disconnect() {
        active.set(false);
        connected.set(false);
        WebSocket ws = this.webSocket;
        if (ws != null) {
            try {
                ws.sendClose(WebSocket.NORMAL_CLOSURE, "QuantStream disconnect requested");
            } catch (Exception ignored) {}
            this.webSocket = null;
        }
        logger.info("Disconnected from Finnhub live market data provider");
    }

    /**
     * Returns "CONNECTED", "DISCONNECTED", or "UNCONFIGURED" (if API key is missing).
     */
    @Override
    public String healthStatus() {
        if (apiKey == null || apiKey.isBlank()) {
            return "UNCONFIGURED";
        }
        return connected.get() ? "CONNECTED" : "DISCONNECTED";
    }

    @Override
    public void setTickListener(Consumer<StockTick> tickListener) {
        this.tickListener = tickListener != null ? tickListener : tick -> {};
    }

    public Set<String> getSubscribedSymbols() {
        return Collections.unmodifiableSet(subscribedSymbols);
    }

    public String getProviderName() {
        return providerName;
    }

    /**
     * Sends the JSON subscription frame to Finnhub: {"type":"subscribe","symbol":"AAPL"}
     */
    private void sendSubscribe(WebSocket ws, String symbol) {
        try {
            String msg = String.format("{\"type\":\"subscribe\",\"symbol\":\"%s\"}", symbol);
            ws.sendText(msg, true);
            logger.debug("Sent Finnhub subscribe frame: {}", msg);
        } catch (Exception e) {
            logger.warn("Failed to send Finnhub subscribe message for {}: {}", symbol, e.getMessage());
        }
    }

    /**
     * Sends the JSON unsubscribe frame to Finnhub: {"type":"unsubscribe","symbol":"AAPL"}
     */
    private void sendUnsubscribe(WebSocket ws, String symbol) {
        try {
            String msg = String.format("{\"type\":\"unsubscribe\",\"symbol\":\"%s\"}", symbol);
            ws.sendText(msg, true);
            logger.debug("Sent Finnhub unsubscribe frame: {}", msg);
        } catch (Exception e) {
            logger.warn("Failed to send Finnhub unsubscribe message for {}: {}", symbol, e.getMessage());
        }
    }

    /**
     * Schedules a reconnect after a delay, doubling the wait time on each failure.
     */
    private void scheduleReconnect() {
        if (!active.get()) return;
        int delay = retryBackoffSeconds;
        retryBackoffSeconds = Math.min(30, retryBackoffSeconds * 2);
        logger.info("Scheduling Finnhub WebSocket reconnect attempt in {} seconds...", delay);
        reconnectScheduler.schedule(this::connect, delay, TimeUnit.SECONDS);
    }

    @PreDestroy
    public void shutdown() {
        disconnect();
        reconnectScheduler.shutdownNow();
    }

    /**
     * Non-blocking WebSocket listener that receives data frames directly from Finnhub.
     */
    private class FinnhubWebSocketListener implements WebSocket.Listener {
        private final StringBuilder buffer = new StringBuilder();

        @Override
        public void onOpen(WebSocket webSocket) {
            logger.info("Finnhub live WebSocket session established");
            WebSocket.Listener.super.onOpen(webSocket);
        }

        /**
         * Called automatically whenever a text message frame arrives from Finnhub.
         */
        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            buffer.append(data);
            if (last) {
                String payload = buffer.toString();
                buffer.setLength(0);

                // Parse the JSON payload into StockTick records
                List<StockTick> ticks = tradeParser.parse(payload);
                // Dispatch each trade to the listener
                for (StockTick tick : ticks) {
                    try {
                        tickListener.accept(tick);
                    } catch (Exception e) {
                        logger.error("Error dispatching Finnhub live tick {}: {}", tick.symbol(), e.getMessage());
                    }
                }
            }
            return WebSocket.Listener.super.onText(webSocket, data, last);
        }

        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            connected.set(false);
            logger.warn("Finnhub WebSocket closed by remote server: status={}, reason={}", statusCode, reason);
            if (active.get()) {
                scheduleReconnect();
            }
            return WebSocket.Listener.super.onClose(webSocket, statusCode, reason);
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            connected.set(false);
            logger.warn("Finnhub WebSocket error encountered: {}", error.getMessage());
            if (active.get()) {
                scheduleReconnect();
            }
        }
    }
}
