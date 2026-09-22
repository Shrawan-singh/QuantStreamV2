'use client';

/**
 * ==============================================================================
 * Real-Time WebSocket Hook (frontend/src/lib/useQuantStreamWebSocket.js)
 * ==============================================================================
 *
 * WHAT IS THIS FILE FOR? (Plain English):
 * Imagine a high-speed stock trading floor. Instead of having to press "Refresh"
 * (F5) on your browser every second to see new prices, this file opens a direct,
 * permanent telephone line (a "WebSocket") between your web browser and the
 * Spring Boot backend server.
 *
 * Whenever a stock price changes on the server:
 * 1. The server blasts the new price down this open phone line.
 * 2. This hook catches it instantly.
 * 3. It checks: "Did the price go up or down since the last tick?"
 *    - If up: marks it green ('up') so the UI can flash green.
 *    - If down: marks it red ('down') so the UI can flash red.
 * 4. It notifies React, which smoothly updates the charts, tables, and gauges.
 *
 * WHAT IS A CUSTOM REACT HOOK?
 * In React, functions starting with `use...` are called "Hooks".
 * This hook packages all the complex networking, reconnecting, and error-handling
 * into one neat package so any visual component can simply call:
 *   const { marketData, connectionStatus } = useQuantStreamWebSocket();
 * ==============================================================================
 */

import { useEffect, useState, useRef, useCallback } from 'react';
import { Client } from '@stomp/stompjs';
import SockJS from 'sockjs-client';

// The URL of our Spring Boot backend REST & WebSocket server.
// Reads from environment variables (for Docker/production) or defaults to localhost:8080.
const API_BASE = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080';
const WS_ENDPOINT = `${API_BASE}/ws`;

export function useQuantStreamWebSocket() {
  // --- REACT STATE VARIABLES (Things that make the UI re-render when they change) ---
  
  // A dictionary/map of all stocks: { "AAPL": { price: 182.5, sma20: 180.1, ... }, "NVDA": { ... } }
  const [marketData, setMarketData] = useState({});
  
  // Connection status indicator: 'CONNECTING', 'CONNECTED', or 'DISCONNECTED'
  const [connectionStatus, setConnectionStatus] = useState('CONNECTING');
  
  // Timestamp of the very last price tick received (for showing "Last tick: 2s ago")
  const [lastTickTime, setLastTickTime] = useState(null);
  
  // Total counter of all ticks processed since the page was opened
  const [totalTicksReceived, setTotalTicksReceived] = useState(0);
  
  // Market mode: 'simulation' (NSE Indian equities) or 'live' (Finnhub US equities)
  const [marketConfig, setMarketConfig] = useState({
    mode: 'simulation',
    provider: 'finnhub',
    status: 'ACTIVE'
  });
  
  // The most recent trading alert event (e.g., "RSI Overbought on RELIANCE")
  const [latestAlertEvent, setLatestAlertEvent] = useState(null);

  // --- REFS (Variables that store data without triggering screen re-renders) ---
  const clientRef = useRef(null);
  // Keeps track of the last known price for each symbol so we can calculate up/down flash
  const lastPricesRef = useRef({});

  /**
   * Processes a single stock price snapshot received from the server.
   * Compares the new price against the old price to determine whether to flash GREEN or RED.
   */
  const handleIncomingSnapshot = useCallback((snapshot) => {
    if (!snapshot || !snapshot.symbol) return;

    const sym = snapshot.symbol.toUpperCase();
    const prevPrice = lastPricesRef.current[sym];
    let tickDirection = 'none';

    // Check price movement for visual UI flash animation
    if (prevPrice !== undefined && snapshot.price !== undefined) {
      if (Number(snapshot.price) > Number(prevPrice)) {
        tickDirection = 'up';   // Price rose -> flash green
      } else if (Number(snapshot.price) < Number(prevPrice)) {
        tickDirection = 'down'; // Price fell -> flash red
      }
    }
    // Remember this price as the new baseline for next time
    lastPricesRef.current[sym] = snapshot.price;

    // Merge this updated stock into our global marketData dictionary
    setMarketData((prev) => ({
      ...prev,
      [sym]: {
        ...snapshot,
        tickDirection,
        lastUpdated: Date.now(),
      },
    }));

    setLastTickTime(new Date());
    setTotalTicksReceived((c) => c + 1);
  }, []);

  /**
   * STEP 1: INITIAL DATA HYDRATION (REST API)
   * Before WebSocket finishes connecting, we make a quick HTTP GET request to:
   * 1. `/api/stocks`: Immediately fetch current prices so the dashboard isn't blank!
   * 2. `/api/config`: Discover whether we are in Live or Simulation mode.
   * 3. Set up a periodic health check every 10 seconds.
   */
  useEffect(() => {
    let mounted = true;
    async function fetchInitialData() {
      try {
        const [stocksRes, configRes] = await Promise.allSettled([
          fetch(`${API_BASE}/api/stocks`),
          fetch(`${API_BASE}/api/config`)
        ]);

        if (stocksRes.status === 'fulfilled' && stocksRes.value.ok) {
          const list = await stocksRes.value.json();
          if (mounted && Array.isArray(list)) {
            const initialMap = {};
            list.forEach((item) => {
              initialMap[item.symbol.toUpperCase()] = {
                ...item,
                tickDirection: 'none',
                lastUpdated: Date.now(),
              };
              lastPricesRef.current[item.symbol.toUpperCase()] = item.price;
            });
            setMarketData((prev) => ({ ...initialMap, ...prev }));
          }
        }

        if (configRes.status === 'fulfilled' && configRes.value.ok) {
          const cfg = await configRes.value.json();
          if (mounted && cfg) {
            setMarketConfig({
              mode: cfg.marketData?.mode || cfg.marketDataMode || 'simulation',
              provider: cfg.marketData?.provider || cfg.marketDataProvider || 'finnhub',
              status: cfg.marketData?.status || cfg.marketDataStatus || 'ACTIVE'
            });
          }
        }
      } catch (err) {
        console.debug('Initial REST fetch waiting for backend...', err.message);
      }
    }

    fetchInitialData();
    // Re-verify health/status every 10 seconds
    const statusInterval = setInterval(async () => {
      try {
        const res = await fetch(`${API_BASE}/api/health`);
        if (res.ok && mounted) {
          const health = await res.json();
          setMarketConfig((prev) => ({
            ...prev,
            mode: health.marketDataMode || prev.mode,
            provider: health.marketDataProvider || prev.provider,
            status: health.marketDataStatus || prev.status
          }));
        }
      } catch (e) {}
    }, 10000);

    return () => {
      mounted = false;
      clearInterval(statusInterval);
    };
  }, []);

  /**
   * STEP 2: STOMP OVER SOCKJS WEBSOCKET CONNECTION
   * Connects to `/ws` on the backend and subscribes to real-time broadcast channels:
   * - `/topic/market/all`: Stream of live ticks and calculated indicators
   * - `/topic/alerts`: Live notifications when user alert thresholds are crossed
   *
   * FALLBACK POLLING SAFETY NET:
   * If the WebSocket connection ever drops (e.g., poor WiFi, server restart),
   * this code automatically falls back to polling `/api/stocks` every 2 seconds
   * so the user's trading screen never freezes!
   */
  useEffect(() => {
    let client = null;
    let fallbackInterval = null;

    try {
      client = new Client({
        // SockJS provides fallback compatibility if native WebSocket is blocked
        webSocketFactory: () => new SockJS(WS_ENDPOINT),
        reconnectDelay: 3000,      // Try to reconnect every 3 seconds if disconnected
        heartbeatIncoming: 4000,   // Expect heartbeat ping from server every 4 seconds
        heartbeatOutgoing: 4000,   // Send heartbeat ping to server every 4 seconds
        debug: (str) => {
          // Debug logs can be enabled here if troubleshooting network traffic
        },
        onConnect: () => {
          setConnectionStatus('CONNECTED');
          // If fallback polling was running, shut it down now that live WS is back!
          if (fallbackInterval) {
            clearInterval(fallbackInterval);
            fallbackInterval = null;
          }

          // Subscribe to live market ticks
          client.subscribe('/topic/market/all', (message) => {
            try {
              const snapshot = JSON.parse(message.body);
              handleIncomingSnapshot(snapshot);
            } catch (e) {
              console.error('Failed to parse STOMP message', e);
            }
          });

          // Subscribe to live trade alerts
          client.subscribe('/topic/alerts', (message) => {
            try {
              const alertNotif = JSON.parse(message.body);
              setLatestAlertEvent(alertNotif);
            } catch (e) {
              console.error('Failed to parse alert notification', e);
            }
          });
        },
        onDisconnect: () => {
          setConnectionStatus('DISCONNECTED');
        },
        onStompError: (frame) => {
          console.warn('STOMP error:', frame.headers['message']);
          setConnectionStatus('DISCONNECTED');
        },
        onWebSocketClose: () => {
          setConnectionStatus('DISCONNECTED');
          // Start fallback polling if WebSocket closes unexpectedly
          if (!fallbackInterval) {
            fallbackInterval = setInterval(async () => {
              try {
                const res = await fetch(`${API_BASE}/api/stocks`);
                if (res.ok) {
                  const list = await res.json();
                  if (Array.isArray(list)) {
                    list.forEach(handleIncomingSnapshot);
                  }
                }
              } catch (e) {}
            }, 2000);
          }
        },
      });

      client.activate();
      clientRef.current = client;
    } catch (err) {
      console.warn('WebSocket setup exception:', err);
      setConnectionStatus('DISCONNECTED');
    }

    // CLEANUP FUNCTION: Cleanly close connection when user leaves the page
    return () => {
      if (client) {
        client.deactivate();
      }
      if (fallbackInterval) {
        clearInterval(fallbackInterval);
      }
    };
  }, [handleIncomingSnapshot]);

  // Expose these state values to whatever React component calls this hook!
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

