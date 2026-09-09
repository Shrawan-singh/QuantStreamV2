'use client';

import { useEffect, useState, useRef, useCallback } from 'react';
import { Client } from '@stomp/stompjs';
import SockJS from 'sockjs-client';

const API_BASE = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080';
const WS_ENDPOINT = `${API_BASE}/ws`;

export function useQuantStreamWebSocket() {
  const [marketData, setMarketData] = useState({});
  const [connectionStatus, setConnectionStatus] = useState('CONNECTING'); // CONNECTED, CONNECTING, DISCONNECTED
  const [lastTickTime, setLastTickTime] = useState(null);
  const [totalTicksReceived, setTotalTicksReceived] = useState(0);
  const [marketConfig, setMarketConfig] = useState({
    mode: 'simulation',
    provider: 'finnhub',
    status: 'ACTIVE'
  });
  const [latestAlertEvent, setLatestAlertEvent] = useState(null);
  const clientRef = useRef(null);
  const lastPricesRef = useRef({});

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
        ...snapshot,
        tickDirection,
        lastUpdated: Date.now(),
      },
    }));

    setLastTickTime(new Date());
    setTotalTicksReceived((c) => c + 1);
  }, []);

  // Initial fetch via REST to populate immediately before first WS tick, plus config check
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

  // STOMP WebSocket Connection
  useEffect(() => {
    let client = null;
    let fallbackInterval = null;

    try {
      client = new Client({
        webSocketFactory: () => new SockJS(WS_ENDPOINT),
        reconnectDelay: 3000,
        heartbeatIncoming: 4000,
        heartbeatOutgoing: 4000,
        debug: (str) => {
          // logger debug if needed
        },
        onConnect: () => {
          setConnectionStatus('CONNECTED');
          if (fallbackInterval) {
            clearInterval(fallbackInterval);
            fallbackInterval = null;
          }

          client.subscribe('/topic/market/all', (message) => {
            try {
              const snapshot = JSON.parse(message.body);
              handleIncomingSnapshot(snapshot);
            } catch (e) {
              console.error('Failed to parse STOMP message', e);
            }
          });

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
          // Start fallback polling if disconnected
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

    return () => {
      if (client) {
        client.deactivate();
      }
      if (fallbackInterval) {
        clearInterval(fallbackInterval);
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
