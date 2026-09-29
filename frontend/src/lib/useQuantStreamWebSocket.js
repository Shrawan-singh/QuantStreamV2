'use client';

/**
 * ==============================================================================
 * Real-Time WebSocket Hook (frontend/src/lib/useQuantStreamWebSocket.js)
 * ==============================================================================
 *
 * Provides real-time market data streaming across the 243-stock simulation universe.
 * If a Spring Boot backend is connected, it streams live STOMP ticks over WebSockets.
 * If in standalone simulation mode (or on Vercel before backend is linked),
 * it runs a high-frequency (20-30 ticks/second) realistic order flow generator
 * so the terminal is immediately alive with real-time ticks, price action, and charts.
 * ==============================================================================
 */

import { useEffect, useState, useRef, useCallback } from 'react';
import { Client } from '@stomp/stompjs';
import SockJS from 'sockjs-client';
import { FULL_SIMULATION_UNIVERSE } from './simulationUniverse';

// The URL of our Spring Boot backend REST & WebSocket server.
const API_BASE = process.env.NEXT_PUBLIC_API_URL || (typeof window !== 'undefined' && window.location.hostname === 'localhost' ? 'http://localhost:8080' : '');
const WS_ENDPOINT = API_BASE ? `${API_BASE}/ws` : '';

export function useQuantStreamWebSocket() {
  // Initialize marketData with all 243 instruments so the cockpit is fully populated on first frame
  const [marketData, setMarketData] = useState(() => {
    const initialMap = {};
    FULL_SIMULATION_UNIVERSE.forEach((item) => {
      initialMap[item.symbol] = {
        ...item,
        tickDirection: 'none',
        lastUpdated: Date.now(),
      };
    });
    return initialMap;
  });

  const [connectionStatus, setConnectionStatus] = useState('CONNECTED');
  const [lastTickTime, setLastTickTime] = useState(() => new Date());
  const [totalTicksReceived, setTotalTicksReceived] = useState(0);

  const [marketConfig, setMarketConfig] = useState({
    mode: 'simulation',
    provider: 'finnhub',
    status: 'ACTIVE',
  });

  const [latestAlertEvent, setLatestAlertEvent] = useState(null);

  const clientRef = useRef(null);
  const lastPricesRef = useRef({});
  const isBackendConnectedRef = useRef(false);

  // Initialize lastPricesRef
  useEffect(() => {
    FULL_SIMULATION_UNIVERSE.forEach((item) => {
      lastPricesRef.current[item.symbol] = item.price;
    });
  }, []);

  /**
   * Processes an incoming price snapshot (from either live WebSocket or simulation engine).
   */
  const handleIncomingSnapshot = useCallback((snapshot) => {
    if (!snapshot || !snapshot.symbol) return;

    const sym = snapshot.symbol.toUpperCase();
    const prevPrice = lastPricesRef.current[sym];
    let tickDirection = 'none';

    if (prevPrice !== undefined && snapshot.price !== undefined) {
      if (Number(snapshot.price) > Number(prevPrice)) {
        tickDirection = 'up';
      } else if (Number(snapshot.price) < Number(prevPrice)) {
        tickDirection = 'down';
      }
    }
    lastPricesRef.current[sym] = snapshot.price;

    setMarketData((prev) => ({
      ...prev,
      [sym]: {
        ...(prev[sym] || {}),
        ...snapshot,
        tickDirection,
        lastUpdated: Date.now(),
      },
    }));

    setLastTickTime(new Date());
    setTotalTicksReceived((c) => c + 1);
  }, []);

  /**
   * HIGH-FREQUENCY REAL-TIME SIMULATION ENGINE (20 - 30 ticks/second)
   * Streams continuous order fills, price changes, and indicator drift across all 243 stocks.
   * Yields automatically when a real Spring Boot backend WebSocket is connected.
   */
  useEffect(() => {
    const highVolumeSymbols = [
      'RELIANCE', 'TCS', 'HDFCBANK', 'INFY', 'TATAMOTORS', 'NVDA', 'AAPL',
      'MSFT', 'TSLA', 'ICICIBANK', 'BHARTIARTL', 'SBIN', 'ITC', 'LT', 'MARUTI', 'BAJFINANCE'
    ];

    const simulationInterval = setInterval(() => {
      // If live backend WebSocket is actively streaming, pause client-side simulation
      if (isBackendConnectedRef.current) return;

      // 2 to 4 concurrent order fills every 120ms (~20-30 ticks/sec)
      const batchSize = Math.floor(Math.random() * 3) + 2;

      for (let b = 0; b < batchSize; b++) {
        let stock;
        if (Math.random() < 0.5) {
          const sym = highVolumeSymbols[Math.floor(Math.random() * highVolumeSymbols.length)];
          stock = FULL_SIMULATION_UNIVERSE.find((s) => s.symbol === sym) || FULL_SIMULATION_UNIVERSE[0];
        } else {
          stock = FULL_SIMULATION_UNIVERSE[Math.floor(Math.random() * FULL_SIMULATION_UNIVERSE.length)];
        }

        // Geometric Brownian price motion with gentle mean reversion to prevClose
        const meanReversionDrift = (stock.prevClose - stock.price) * 0.0008;
        const randomShock = (Math.random() - 0.496) * 0.0035;
        const deltaPct = randomShock + meanReversionDrift;

        const newPrice = Math.max(1.0, Number((stock.price * (1 + deltaPct)).toFixed(2)));
        const change = Number((newPrice - stock.prevClose).toFixed(2));
        const changePct = Number(((change / stock.prevClose) * 100).toFixed(2));

        stock.price = newPrice;
        stock.priceChange = change;
        stock.priceChangePercent = changePct;
        stock.openPrice = stock.openPrice || stock.prevClose || Number((newPrice * 0.996).toFixed(2));
        stock.highPrice = Math.max(stock.highPrice || newPrice, newPrice);
        stock.lowPrice = Math.min(stock.lowPrice || newPrice, newPrice);
        stock.volume = (stock.volume || 500000) + Math.floor(Math.random() * 800) + 75;

        // Realistic Technical Indicator Drift
        const rsiDelta = (deltaPct > 0 ? 0.35 : -0.35) + (Math.random() - 0.5) * 0.4;
        stock.rsi14 = Math.min(92, Math.max(12, Number((stock.rsi14 + rsiDelta).toFixed(1))));

        const scoreDelta = (deltaPct > 0 ? 0.5 : -0.5) + (Math.random() - 0.5) * 0.6;
        stock.convictionScore = Math.min(99, Math.max(15, Number((stock.convictionScore + scoreDelta).toFixed(1))));
        stock.scoreCategory = stock.convictionScore >= 75 ? 'VERY_STRONG'
          : stock.convictionScore >= 60 ? 'STRONG'
          : stock.convictionScore >= 45 ? 'NEUTRAL'
          : stock.convictionScore >= 30 ? 'WEAK' : 'VERY_WEAK';

        stock.ready = true;

        handleIncomingSnapshot({
          ...stock,
          timestamp: new Date().toISOString(),
        });
      }
    }, 120);

    return () => clearInterval(simulationInterval);
  }, [handleIncomingSnapshot]);

  /**
   * OPTIONAL BACKEND REST & STOMP WEBSOCKET CONNECTION
   * Only attempts connection if a valid API URL exists (avoiding mixed-content warnings on HTTPS).
   */
  useEffect(() => {
    if (!WS_ENDPOINT) return;

    let client = null;
    let mounted = true;

    // Fetch initial REST data from backend if available
    async function fetchBackendData() {
      try {
        const res = await fetch(`${API_BASE}/api/stocks`, { signal: AbortSignal.timeout(2000) });
        if (res.ok && mounted) {
          const list = await res.json();
          if (Array.isArray(list) && list.length > 0) {
            list.forEach(handleIncomingSnapshot);
          }
        }
      } catch (e) {}
    }

    fetchBackendData();

    // Establish STOMP over SockJS connection
    try {
      client = new Client({
        webSocketFactory: () => new SockJS(WS_ENDPOINT),
        reconnectDelay: 5000,
        heartbeatIncoming: 4000,
        heartbeatOutgoing: 4000,
        onConnect: () => {
          if (!mounted) return;
          isBackendConnectedRef.current = true;
          setConnectionStatus('CONNECTED');

          client.subscribe('/topic/market/all', (message) => {
            try {
              const snapshot = JSON.parse(message.body);
              handleIncomingSnapshot(snapshot);
            } catch (e) {}
          });

          client.subscribe('/topic/alerts', (message) => {
            try {
              const alertNotif = JSON.parse(message.body);
              setLatestAlertEvent(alertNotif);
            } catch (e) {}
          });
        },
        onDisconnect: () => {
          isBackendConnectedRef.current = false;
        },
        onStompError: () => {
          isBackendConnectedRef.current = false;
        },
        onWebSocketClose: () => {
          isBackendConnectedRef.current = false;
        },
      });

      client.activate();
      clientRef.current = client;
    } catch (e) {
      isBackendConnectedRef.current = false;
    }

    return () => {
      mounted = false;
      if (client) {
        client.deactivate();
      }
    };
  }, [handleIncomingSnapshot]);

  return {
    marketData,
    connectionStatus,
    lastTickTime,
    totalTicksReceived,
    latestAlertEvent,
    marketConfig,
    apiBase: API_BASE,
  };
}


