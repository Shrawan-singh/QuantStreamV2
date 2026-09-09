package com.quantstream.backend.service;

import com.quantstream.backend.analytics.AnalyticsEngine;
import com.quantstream.backend.analytics.indicator.EmaIndicator;
import com.quantstream.backend.analytics.indicator.MomentumIndicator;
import com.quantstream.backend.analytics.indicator.RelativeVolumeIndicator;
import com.quantstream.backend.analytics.indicator.RsiIndicator;
import com.quantstream.backend.analytics.indicator.SmaIndicator;
import com.quantstream.backend.analytics.scoring.ConvictionScoreEngine;
import com.quantstream.backend.analytics.state.MarketStateStore;
import com.quantstream.backend.config.IndicatorProperties;
import com.quantstream.backend.config.ScoringProperties;
import com.quantstream.backend.domain.StockTick;
import com.quantstream.backend.domain.TickEventType;
import com.quantstream.backend.domain.TickSource;
import com.quantstream.backend.domain.dto.AlertTriggerNotification;
import com.quantstream.backend.domain.entity.AlertConfigEntity;
import com.quantstream.backend.domain.entity.AlertTriggerHistoryEntity;
import com.quantstream.backend.processing.TickProcessingService;
import com.quantstream.backend.processing.TickValidationService;
import com.quantstream.backend.repository.AlertConfigRepository;
import com.quantstream.backend.repository.AlertTriggerHistoryRepository;
import com.quantstream.backend.websocket.MarketWebSocketService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@DisplayName("End-to-End Alert Execution & One-Shot Trigger Pipeline Test")
@SuppressWarnings("null")
class AlertExecutionEndToEndTest {

    private AlertConfigRepository alertConfigRepository;
    private AlertTriggerHistoryRepository alertTriggerHistoryRepository;
    private SimpMessagingTemplate messagingTemplate;
    private AlertExecutionService alertExecutionService;
    private TickProcessingService tickProcessingService;
    private AnalyticsEngine analyticsEngine;

    private final List<AlertConfigEntity> inMemoryAlerts = new ArrayList<>();
    private final List<AlertTriggerHistoryEntity> inMemoryHistory = new ArrayList<>();

    @BeforeEach
    void setUp() {
        inMemoryAlerts.clear();
        inMemoryHistory.clear();

        alertConfigRepository = Mockito.mock(AlertConfigRepository.class);
        alertTriggerHistoryRepository = Mockito.mock(AlertTriggerHistoryRepository.class);
        messagingTemplate = Mockito.mock(SimpMessagingTemplate.class);

        // Setup mock repository behavior using in-memory state
        when(alertConfigRepository.findBySymbolAndEnabledTrueAndTriggeredFalse(anyString()))
                .thenAnswer(inv -> {
                    String sym = inv.getArgument(0);
                    return inMemoryAlerts.stream()
                            .filter(a -> a.getSymbol().equalsIgnoreCase(sym) && a.isEnabled() && !a.isTriggered())
                            .toList();
                });

        when(alertConfigRepository.save(any(AlertConfigEntity.class))).thenAnswer(inv -> {
            AlertConfigEntity a = inv.getArgument(0);
            if (a.getId() == null) {
                a.setId((long) (inMemoryAlerts.size() + 1));
                inMemoryAlerts.add(a);
            }
            return a;
        });

        when(alertConfigRepository.findById(anyLong())).thenAnswer(inv -> {
            Long id = inv.getArgument(0);
            return inMemoryAlerts.stream().filter(a -> a.getId().equals(id)).findFirst();
        });

        when(alertTriggerHistoryRepository.save(any(AlertTriggerHistoryEntity.class))).thenAnswer(inv -> {
            AlertTriggerHistoryEntity h = inv.getArgument(0);
            h.setId((long) (inMemoryHistory.size() + 1));
            inMemoryHistory.add(h);
            return h;
        });

        alertExecutionService = new AlertExecutionService(
                alertConfigRepository,
                alertTriggerHistoryRepository,
                messagingTemplate
        );

        IndicatorProperties indProps = IndicatorProperties.defaultProperties();
        ScoringProperties scProps = ScoringProperties.defaultProperties();
        analyticsEngine = new AnalyticsEngine(
                new MarketStateStore(),
                new SmaIndicator(indProps),
                new EmaIndicator(indProps),
                new RsiIndicator(indProps),
                new MomentumIndicator(indProps),
                new RelativeVolumeIndicator(indProps),
                new ConvictionScoreEngine(scProps)
        );

        MarketWebSocketService marketWsService = Mockito.mock(MarketWebSocketService.class);
        AnalyticsPersistenceService persistenceService = Mockito.mock(AnalyticsPersistenceService.class);

        tickProcessingService = new TickProcessingService(
                new TickValidationService(),
                analyticsEngine,
                marketWsService,
                persistenceService,
                alertExecutionService
        );
    }

    private StockTick createTick(String symbol, double price) {
        return new StockTick(
                UUID.randomUUID(),
                symbol,
                BigDecimal.valueOf(price).setScale(2, java.math.RoundingMode.HALF_UP),
                100_000L,
                Instant.now(),
                TickEventType.TRADE,
                TickSource.SIMULATION,
                "NSE"
        );
    }

    @Test
    @DisplayName("End-to-End: PRICE_ABOVE threshold crossing triggers alert exactly once")
    void testPriceAboveAlertTriggerFlow() {
        // 1. Create RELIANCE PRICE_ABOVE 2850.00
        AlertConfigEntity alert = new AlertConfigEntity("RELIANCE", "PRICE_ABOVE", new BigDecimal("2850.00"), true);
        alert.setId(1L);
        inMemoryAlerts.add(alert);

        assertEquals("ACTIVE", alert.getState());
        assertFalse(alert.isTriggered());
        assertTrue(alert.isEnabled());

        // 2. Feed ticks below 2850
        tickProcessingService.process(createTick("RELIANCE", 2840.00));
        tickProcessingService.process(createTick("RELIANCE", 2849.50));

        // Confirm alert remains ACTIVE
        assertEquals("ACTIVE", alert.getState());
        assertFalse(alert.isTriggered());
        assertTrue(alert.isEnabled());
        assertEquals(0, inMemoryHistory.size());
        verify(messagingTemplate, never()).convertAndSend(eq("/topic/alerts"), any(Object.class));

        // 3. Feed tick at 2851.00 (threshold crossed!)
        tickProcessingService.process(createTick("RELIANCE", 2851.00));

        // Confirm alert becomes TRIGGERED
        assertEquals("TRIGGERED", alert.getState());
        assertTrue(alert.isTriggered());
        assertFalse(alert.isEnabled()); // Disabled by one-shot semantics
        assertEquals(new BigDecimal("2851.00"), alert.getTriggeredValue());
        assertNotNull(alert.getTriggeredAt());

        // Confirm trigger history recorded
        assertEquals(1, inMemoryHistory.size());
        AlertTriggerHistoryEntity history = inMemoryHistory.get(0);
        assertEquals(1L, history.getAlertId());
        assertEquals("RELIANCE", history.getSymbol());
        assertEquals("PRICE_ABOVE", history.getConditionType());
        assertEquals(new BigDecimal("2850.00"), history.getThreshold());
        assertEquals(new BigDecimal("2851.00"), history.getTriggeredValue());

        // Confirm WebSocket event emitted
        ArgumentCaptor<AlertTriggerNotification> captor = ArgumentCaptor.forClass(AlertTriggerNotification.class);
        verify(messagingTemplate, times(1)).convertAndSend(eq("/topic/alerts"), captor.capture());
        AlertTriggerNotification notification = captor.getValue();
        assertEquals("RELIANCE", notification.symbol());
        assertEquals(new BigDecimal("2851.00"), notification.triggeredValue());
        assertEquals("TRIGGERED", notification.state());

        // 4. One-shot semantics: Subsequent ticks above threshold do NOT trigger again
        tickProcessingService.process(createTick("RELIANCE", 2855.00));
        tickProcessingService.process(createTick("RELIANCE", 2860.00));

        // History count remains exactly 1, WebSocket called only once
        assertEquals(1, inMemoryHistory.size());
        verify(messagingTemplate, times(1)).convertAndSend(eq("/topic/alerts"), any(Object.class));
    }

    @Test
    @DisplayName("End-to-End: PRICE_BELOW threshold crossing triggers alert exactly once")
    void testPriceBelowAlertTriggerFlow() {
        // 1. Create TCS PRICE_BELOW 3400.00
        AlertConfigEntity alert = new AlertConfigEntity("TCS", "PRICE_BELOW", new BigDecimal("3400.00"), true);
        alert.setId(2L);
        inMemoryAlerts.add(alert);

        // 2. Feed ticks above 3400
        tickProcessingService.process(createTick("TCS", 3450.00));
        tickProcessingService.process(createTick("TCS", 3405.00));

        assertEquals("ACTIVE", alert.getState());
        assertEquals(0, inMemoryHistory.size());

        // 3. Feed tick below 3400 (at 3390.00)
        tickProcessingService.process(createTick("TCS", 3390.00));

        assertEquals("TRIGGERED", alert.getState());
        assertTrue(alert.isTriggered());
        assertEquals(new BigDecimal("3390.00"), alert.getTriggeredValue());
        assertEquals(1, inMemoryHistory.size());

        // Confirm WebSocket message sent
        verify(messagingTemplate, times(1)).convertAndSend(eq("/topic/alerts"), any(AlertTriggerNotification.class));

        // 4. Feed further ticks below threshold
        tickProcessingService.process(createTick("TCS", 3380.00));
        assertEquals(1, inMemoryHistory.size());
    }

    @Test
    @DisplayName("Resetting a triggered alert re-arms it to ACTIVE state")
    void testResetTriggeredAlert() {
        AlertConfigEntity alert = new AlertConfigEntity("INFY", "PRICE_ABOVE", new BigDecimal("1600.00"), true);
        alert.setId(3L);
        inMemoryAlerts.add(alert);

        // Trigger alert
        tickProcessingService.process(createTick("INFY", 1610.00));
        assertEquals("TRIGGERED", alert.getState());

        // Re-arm via resetAlert
        Optional<AlertConfigEntity> resetOpt = alertExecutionService.resetAlert(3L);
        assertTrue(resetOpt.isPresent());
        AlertConfigEntity resetAlert = resetOpt.get();

        assertEquals("ACTIVE", resetAlert.getState());
        assertFalse(resetAlert.isTriggered());
        assertTrue(resetAlert.isEnabled());
        assertNull(resetAlert.getTriggeredValue());

        // Ticks below threshold -> remains ACTIVE
        tickProcessingService.process(createTick("INFY", 1590.00));
        assertEquals("ACTIVE", resetAlert.getState());

        // Re-cross threshold -> triggers again
        tickProcessingService.process(createTick("INFY", 1605.00));
        assertEquals("TRIGGERED", resetAlert.getState());
        assertEquals(2, inMemoryHistory.size());
    }
}
