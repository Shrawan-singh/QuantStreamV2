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
 *
 * <p>Connects to {@code wss://ws.finnhub.io?token=${FINNHUB_API_KEY}} using Java standard
 * {@link java.net.http.HttpClient} WebSocket support. Subscribes to configured equity symbols
 * (e.g., AAPL, MSFT, AMZN, NVDA, GOOGL, META, TSLA) and converts live trade frames into
 * standard {@link StockTick} objects.</p>
 *
 * <p>If the API key is not configured, safely remains in {@code UNCONFIGURED} mode without crashing.</p>
 */
@Service
@ConditionalOnProperty(name = "quantstream.marketdata.mode", havingValue = "live")
public class LiveMarketDataProvider implements MarketDataProvider {

    private static final Logger logger = LoggerFactory.getLogger(LiveMarketDataProvider.class);

    private final FinnhubTradeParser tradeParser;

    @Value("${quantstream.marketdata.provider:FINNHUB}")
    private String providerName;

    @Value("${quantstream.marketdata.api-key:${FINNHUB_API_KEY:}}")
    private String apiKey;

    @Value("${quantstream.marketdata.websocket-url:${FINNHUB_WEBSOCKET_URL:wss://ws.finnhub.io}}")
    private String websocketUrl;

    private final AtomicBoolean connected = new AtomicBoolean(false);
    private final AtomicBoolean active = new AtomicBoolean(false);
    private final Set<String> subscribedSymbols = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private volatile Consumer<StockTick> tickListener = tick -> {};

    private HttpClient httpClient;
    private WebSocket webSocket;
    private final ScheduledExecutorService reconnectScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "finnhub-reconnect-worker");
        t.setDaemon(true);
        return t;
    });

    private volatile int retryBackoffSeconds = 2;

    public LiveMarketDataProvider(FinnhubTradeParser tradeParser) {
        this.tradeParser = tradeParser;
    }

    @Override
    public synchronized void connect() {
        if (apiKey == null || apiKey.isBlank()) {
            logger.warn("Live market data provider '{}' cannot connect: FINNHUB_API_KEY is not configured. " +
                    "Status is UNCONFIGURED. Set FINNHUB_API_KEY in environment to stream live US market ticks.", providerName);
            return;
        }

        active.set(true);

        if (connected.get()) {
            return;
        }

        try {
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

            httpClient.newWebSocketBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .buildAsync(uri, new FinnhubWebSocketListener())
                    .thenAccept(ws -> {
                        this.webSocket = ws;
                        this.connected.set(true);
                        this.retryBackoffSeconds = 2; // reset backoff
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

    private void sendSubscribe(WebSocket ws, String symbol) {
        try {
            String msg = String.format("{\"type\":\"subscribe\",\"symbol\":\"%s\"}", symbol);
            ws.sendText(msg, true);
            logger.debug("Sent Finnhub subscribe frame: {}", msg);
        } catch (Exception e) {
            logger.warn("Failed to send Finnhub subscribe message for {}: {}", symbol, e.getMessage());
        }
    }

    private void sendUnsubscribe(WebSocket ws, String symbol) {
        try {
            String msg = String.format("{\"type\":\"unsubscribe\",\"symbol\":\"%s\"}", symbol);
            ws.sendText(msg, true);
            logger.debug("Sent Finnhub unsubscribe frame: {}", msg);
        } catch (Exception e) {
            logger.warn("Failed to send Finnhub unsubscribe message for {}: {}", symbol, e.getMessage());
        }
    }

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
     * Non-blocking WebSocket listener receiving text frames from Finnhub.
     */
    private class FinnhubWebSocketListener implements WebSocket.Listener {
        private final StringBuilder buffer = new StringBuilder();

        @Override
        public void onOpen(WebSocket webSocket) {
            logger.info("Finnhub live WebSocket session established");
            WebSocket.Listener.super.onOpen(webSocket);
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            buffer.append(data);
            if (last) {
                String payload = buffer.toString();
                buffer.setLength(0);

                List<StockTick> ticks = tradeParser.parse(payload);
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
