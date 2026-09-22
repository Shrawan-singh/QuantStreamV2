'use client';

/**
 * ==============================================================================
 * Main Dashboard Page (frontend/src/app/page.js)
 * ==============================================================================
 *
 * WHAT IS THIS FILE FOR? (Plain English):
 * This is the "Cockpit" of the entire QuantStream web application!
 * Whenever you open the website, Next.js loads this file first.
 *
 * WHAT DOES THIS FILE DO?
 * 1. Hooks into our live WebSocket (`useQuantStreamWebSocket`) to listen for
 *    real-time stock price ticks streaming from the backend.
 * 2. Manages Navigation Tabs:
 *    - DASHBOARD: High-level overview, market breadth, spotlight stock & chart
 *    - SCANNER: High-density spreadsheet/table of all 50-240 tracked stocks
 *    - STOCK_DETAIL: Deep dive into a single stock's indicators (RSI, SMA, EMA, RVOL)
 *    - WATCHLIST: User's customized list of favorite companies
 *    - ALERTS: Set up threshold alerts (e.g., "Tell me if Apple goes over $200")
 *    - ENGINE: Live system metrics (Kafka latency, queue capacity, worker threads)
 * 3. Calculates Market Breadth:
 *    - How many stocks are green (advancing) vs red (declining)?
 *    - What is the average Conviction Score across the whole market?
 *
 * WHAT DOES 'use client' MEAN AT LINE 1?
 * In Next.js, code can run either on the server or inside the user's browser.
 * Adding `'use client'` tells Next.js: "This code needs to run inside the user's
 * web browser so it can listen to clicks, open WebSockets, and update the screen!"
 * ==============================================================================
 */

import React, { useState, useMemo, useEffect } from 'react';
import Sidebar from '../components/Sidebar';
import Header from '../components/Header';
import MarketModeSelector from '../components/MarketModeSelector';
import MarketOverviewGrid from '../components/MarketOverviewGrid';
import ConvictionScoreGauge from '../components/ConvictionScoreGauge';
import InteractiveChart from '../components/InteractiveChart';
import ScannerTable from '../components/ScannerTable';
import WatchlistManager from '../components/WatchlistManager';
import AlertsManager from '../components/AlertsManager';
import EngineHealthView from '../components/EngineHealthView';
import { useQuantStreamWebSocket } from '../lib/useQuantStreamWebSocket';
import { isUsEquityStock, currencySymbol, exchangeBadge } from '../lib/marketUtils';
import {
  TrendingUp,
  TrendingDown,
  Layers,
  Activity,
  ArrowRight,
} from 'lucide-react';

/* ─────────────────────────────────────────────────────────────────────────────
   Pipeline Architecture Steps Strip:
   Explains to the user visually how data travels through QuantStream:
   Tick Ingestion -> Kafka/Buffer -> Indicator Math -> 4-Factor Scoring -> Screen
   ───────────────────────────────────────────────────────────────────────────── */
const PIPELINE_STEPS = [
  { num: '01', name: 'CAPTURE',   desc: 'Low-latency tick ingestion',    icon: 'cloud_download' },
  { num: '02', name: 'STREAM',    desc: 'WebSocket buffer & Kafka',      icon: 'stream' },
  { num: '03', name: 'ANALYZE',   desc: 'RSI, SMA, EMA, Momentum',      icon: 'analytics' },
  { num: '04', name: 'INTERPRET', desc: '4-Factor conviction scoring',   icon: 'psychology' },
  { num: '05', name: 'VISUALIZE', desc: 'Real-time terminal output',     icon: 'query_stats' },
];

// Fallback curated popular stocks shown when a user hasn't created a custom watchlist yet
const CURATED_NSE_SYMBOLS = new Set([
  'RELIANCE', 'TCS', 'HDFCBANK', 'INFY', 'ICICIBANK',
  'SBIN', 'BHARTIARTL', 'ITC', 'LT', 'TATAMOTORS',
]);

const CURATED_US_SYMBOLS = new Set([
  'AAPL', 'MSFT', 'NVDA', 'AMZN', 'GOOGL', 'META', 'TSLA', 'AVGO', 'JPM', 'LLY',
]);

export default function Home() {
  // Connect to the backend WebSocket stream via our custom hook
  const {
    marketData,
    connectionStatus,
    totalTicksReceived,
    lastTickTime,
    latestAlertEvent,
    marketConfig,
    apiBase,
  } = useQuantStreamWebSocket();

  // --- STATE (Memory for user actions) ---
  // Which tab is currently selected in the menu? (Default is DASHBOARD)
  const [activeTab, setActiveTab] = useState('DASHBOARD');
  // Which specific stock did the user click on to inspect?
  const [selectedSymbol, setSelectedSymbol] = useState(null);
  // Is the mobile sidebar drawer open or collapsed?
  const [sidebarOpen, setSidebarOpen] = useState(false);
  // List of symbols the user saved to their personal database watchlist
  const [watchlistSymbols, setWatchlistSymbols] = useState([]);

  // Transform our marketData object { AAPL: {...}, TSLA: {...} } into an easy array [ {...}, {...} ]
  // `useMemo` caches this result so we don't waste CPU cycles re-doing it unnecessarily!
  const stocks = useMemo(() => Object.values(marketData || {}), [marketData]);

  // Load user's watchlist from PostgreSQL database when the page loads
  useEffect(() => {
    let mounted = true;
    async function loadWatchlist() {
      try {
        const res = await fetch(`${apiBase || 'http://localhost:8080'}/api/watchlist`);
        if (res.ok) {
          const list = await res.json();
          if (mounted && Array.isArray(list) && list.length > 0) {
            setWatchlistSymbols(list.map((item) => item.symbol.toUpperCase()));
          }
        }
      } catch (err) {
        // Fallback silently to default curated symbols if database isn't ready
      }
    }
    loadWatchlist();
    return () => {
      mounted = false;
    };
  }, [apiBase, activeTab]);

  // Determine which stock cards to showcase on the top grid of the Dashboard:
  // User's custom watchlist first; if empty, show Top 10 Curated Leaders
  const effectiveCuratedSet = useMemo(() => {
    if (watchlistSymbols.length > 0) {
      return new Set(watchlistSymbols);
    }
    return marketConfig?.mode === 'live' ? CURATED_US_SYMBOLS : CURATED_NSE_SYMBOLS;
  }, [watchlistSymbols, marketConfig?.mode]);

  const displayedStocks = useMemo(() => {
    const curated = stocks.filter((s) => effectiveCuratedSet.has(s.symbol?.toUpperCase()));
    return curated.length > 0 ? curated : stocks.slice(0, 10);
  }, [stocks, effectiveCuratedSet]);

  // Find the exact data for the active spotlight stock
  const activeStock = useMemo(() => {
    if (selectedSymbol && marketData[selectedSymbol]) return marketData[selectedSymbol];
    return stocks[0] || null;
  }, [selectedSymbol, marketData, stocks]);

  const isUsEquity = isUsEquityStock(activeStock, marketConfig);
  const currSym = currencySymbol(activeStock, marketConfig);
  const exchBadge = exchangeBadge(activeStock, marketConfig);

  // MARKET BREADTH: A classic Wall Street indicator!
  // Counts how many stocks are advancing (gaining value) vs declining (losing value)
  const advancing = stocks.filter((s) => (s.priceChangePercent ?? 0) > 0).length;
  const declining = stocks.filter((s) => (s.priceChangePercent ?? 0) < 0).length;
  // Calculate average conviction across the entire tracked universe
  const avgScore =
    stocks.length > 0
      ? stocks.reduce((acc, curr) => acc + (curr.convictionScore ?? 50), 0) / stocks.length
      : 50.0;

  // Helper function to color-code signal badges (Green for Positive, Red for Negative)
  const getSignalBadge = (sig) => {
    switch (sig) {
      case 'POSITIVE':  return { color: 'var(--bullish)', bg: 'var(--bullish-bg)', label: 'POSITIVE' };
      case 'NEGATIVE':  return { color: 'var(--bearish)', bg: 'var(--bearish-bg)', label: 'NEGATIVE' };
      case 'NOT_READY': return { color: 'var(--text-muted)', bg: 'rgba(255,255,255,0.05)', label: 'WARM-UP' };
      default:          return { color: 'var(--neutral)', bg: 'var(--neutral-bg)', label: 'NEUTRAL' };
    }
  };

  return (
    <div className="app-shell">

      {/* ──── Sidebar ──── */}
      <Sidebar
        activeTab={activeTab}
        onSelectTab={setActiveTab}
        connectionStatus={connectionStatus}
        totalTicks={totalTicksReceived}
        marketConfig={marketConfig}
        isOpen={sidebarOpen}
        onClose={() => setSidebarOpen(false)}
      />

      {/* ──── Main Content ──── */}
      <div className="main-content">
        <Header
          connectionStatus={connectionStatus}
          totalTicks={totalTicksReceived}
          marketConfig={marketConfig}
          allInstruments={stocks}
          apiBase={apiBase}
          onSelectSymbol={setSelectedSymbol}
          onSelectTab={setActiveTab}
          onToggleSidebar={() => setSidebarOpen(!sidebarOpen)}
        />

        <main className="page-body">
          {/* ═══ TAB: DASHBOARD ═══ */}
          {activeTab === 'DASHBOARD' && (
            <div className="flex-col gap-xl">
              {/* Market Mode Selector */}
              <MarketModeSelector
                marketConfig={marketConfig}
                connectionStatus={connectionStatus}
                trackedCount={stocks.length}
              />

              {/* Pipeline Architecture Strip */}
              <div className="terminal-panel p-lg mb-lg">
                <div className="label-caps mb-sm">QUANTSTREAM PIPELINE ARCHITECTURE</div>
                <div className="pipeline-strip">
                  {PIPELINE_STEPS.map((step) => (
                    <div className="pipeline-step" key={step.num}>
                      <div className="flex-row items-center justify-between">
                        <span className="pipeline-step-num">{step.num}</span>
                        <span className="material-symbols-outlined" style={{ fontSize: '18px', color: 'var(--text-muted)' }}>
                          {step.icon}
                        </span>
                      </div>
                      <span className="pipeline-step-name">{step.name}</span>
                      <span className="pipeline-step-desc">{step.desc}</span>
                    </div>
                  ))}
                </div>
              </div>

              {/* Market Pulse Summary */}
              <div className="grid-auto grid-auto-fill-sm mb-xl">
                <div className="stat-card">
                  <div className="stat-card-label">MARKET BREADTH</div>
                  <div className="flex-row items-center gap-md">
                    <span className="flex-row items-center gap-xs text-bullish font-bold" style={{ fontSize: '0.92rem' }}>
                      <TrendingUp size={15} /> {advancing} Adv
                    </span>
                    <span className="flex-row items-center gap-xs text-bearish font-bold" style={{ fontSize: '0.92rem' }}>
                      <TrendingDown size={15} /> {declining} Dec
                    </span>
                  </div>
                </div>

                <div className="stat-card">
                  <div className="stat-card-label">AVG CONVICTION</div>
                  <div className="stat-card-value text-accent">
                    {avgScore.toFixed(1)} <span className="text-muted" style={{ fontSize: '0.7rem' }}>/ 100</span>
                  </div>
                </div>

                <div className="stat-card">
                  <div className="stat-card-label">STREAM TOPOLOGY</div>
                  <div className="flex-row items-center gap-sm font-bold" style={{ fontSize: '0.88rem' }}>
                    <Layers size={15} color="var(--accent-indigo)" />
                    <span>Kafka → Queue → 4 Workers</span>
                  </div>
                </div>

                <div className="stat-card">
                  <div className="stat-card-label">DATA SOURCE</div>
                  <div className="flex-row items-center gap-sm font-bold" style={{ fontSize: '0.8rem', color: marketConfig?.mode === 'live' ? 'var(--bullish)' : 'var(--neutral)' }}>
                    <Activity size={14} />
                    <span>{marketConfig?.mode === 'live' ? 'LIVE MARKET • US EQUITIES' : 'SIMULATION • NSE EQUITIES'}</span>
                  </div>
                </div>
              </div>

              {/* Market Overview Grid */}
              <div className="flex-row items-center justify-between mb-base flex-wrap gap-sm">
                <div className="flex-col">
                  <h3 className="section-title" style={{ margin: 0 }}>
                    {watchlistSymbols.length > 0 ? `YOUR WATCHLIST (${displayedStocks.length})` : `CURATED LEADERS (${displayedStocks.length})`}
                  </h3>
                  <span className="section-subtitle" style={{ marginTop: '2px' }}>
                    {watchlistSymbols.length > 0
                      ? 'Live overview of your tracked watchlist symbols'
                      : 'Curated high-liquidity market leaders — click any instrument to inspect'}
                  </span>
                </div>
                <button
                  className="btn-ghost flex-row items-center gap-xs text-accent font-bold"
                  onClick={() => setActiveTab('SCANNER')}
                  style={{ fontSize: '0.78rem' }}
                >
                  View all {stocks.length} instruments in Scanner <ArrowRight size={13} />
                </button>
              </div>

              <MarketOverviewGrid
                stocks={displayedStocks}
                selectedSymbol={activeStock?.symbol}
                onSelectSymbol={setSelectedSymbol}
                onDrillDown={(sym) => {
                  setSelectedSymbol(sym);
                  setActiveTab('STOCK_DETAIL');
                }}
              />

              {/* Active Instrument Spotlight */}
              {activeStock && (
                <div className="mt-xl">
                  <div className="flex-row items-center justify-between mb-base">
                    <h3 className="section-title">
                      SPOTLIGHT: <span className="text-accent">{activeStock.symbol}</span>
                    </h3>
                    <button
                      className="btn-ghost flex-row items-center gap-xs text-accent font-bold"
                      onClick={() => setActiveTab('STOCK_DETAIL')}
                      style={{ fontSize: '0.8rem' }}
                    >
                      Full Analysis <ArrowRight size={14} />
                    </button>
                  </div>

                  <div className="grid-auto grid-auto-fit-xl">
                    <InteractiveChart
                      symbol={activeStock.symbol}
                      currentPrice={activeStock.price}
                      currentSma={activeStock.sma}
                      apiBase={apiBase}
                      isUsEquity={isUsEquity}
                    />
                    <ConvictionScoreGauge snapshot={activeStock} />
                  </div>
                </div>
              )}
            </div>
          )}

          {/* ═══ TAB: SCANNER ═══ */}
          {activeTab === 'SCANNER' && (
            <ScannerTable
              marketData={marketData}
              onSelectSymbol={(sym) => {
                setSelectedSymbol(sym);
                setActiveTab('STOCK_DETAIL');
              }}
              selectedSymbol={selectedSymbol}
              marketConfig={marketConfig}
              apiBase={apiBase}
            />
          )}

          {/* ═══ TAB: STOCK DETAIL ═══ */}
          {activeTab === 'STOCK_DETAIL' && activeStock && (
            <div className="flex-col gap-xl">
              {/* Instrument Header */}
              <div className="terminal-panel p-xl">
                <div className="flex-row justify-between items-start flex-wrap gap-base">
                  <div>
                    <div className="flex-row items-center gap-md">
                      <h1 style={{ fontSize: '2rem', fontWeight: 800, color: '#ffffff', letterSpacing: '-0.02em' }}>
                        {activeStock.symbol}
                      </h1>
                      <span className="badge-exchange">{exchBadge}</span>
                      <span className={`badge badge-pill ${activeStock.source === 'LIVE_PROVIDER' ? 'badge-bullish' : 'badge-neutral'}`}>
                        {activeStock.source === 'LIVE_PROVIDER' ? 'LIVE MARKET STREAM' : 'SIMULATION FEED'}
                      </span>
                    </div>
                    <p className="text-secondary mt-sm" style={{ fontSize: '0.88rem' }}>
                      {activeStock.companyName}
                    </p>
                  </div>

                  <div className="text-right">
                    <div className="mono" style={{ fontSize: '2.4rem', fontWeight: 800, lineHeight: 1 }}>
                      {currSym}{activeStock.price != null ? Number(activeStock.price).toFixed(2) : '--'}
                    </div>
                    <div
                      className="mono mt-sm"
                      style={{
                        fontSize: '0.95rem',
                        fontWeight: 700,
                        color: (activeStock.priceChangePercent ?? 0) >= 0 ? 'var(--bullish)' : 'var(--bearish)',
                      }}
                    >
                      {(activeStock.priceChangePercent ?? 0) >= 0 ? '+' : ''}
                      {activeStock.priceChange != null ? Number(activeStock.priceChange).toFixed(2) : '0.00'} (
                      {(activeStock.priceChangePercent ?? 0) >= 0 ? '+' : ''}
                      {activeStock.priceChangePercent != null ? activeStock.priceChangePercent.toFixed(2) : '0.00'}%)
                    </div>
                  </div>
                </div>

                {/* Session Quick Stats */}
                <hr className="separator" />
                <div className="grid-auto grid-metrics" style={{ fontSize: '0.8rem' }}>
                  <div>
                    <div className="text-muted">SESSION OPEN</div>
                    <div className="mono font-bold mt-sm">{currSym}{activeStock.openPrice != null ? Number(activeStock.openPrice).toFixed(2) : '--'}</div>
                  </div>
                  <div>
                    <div className="text-muted">SESSION HIGH</div>
                    <div className="mono font-bold text-bullish mt-sm">{currSym}{activeStock.highPrice != null ? Number(activeStock.highPrice).toFixed(2) : '--'}</div>
                  </div>
                  <div>
                    <div className="text-muted">SESSION LOW</div>
                    <div className="mono font-bold text-bearish mt-sm">{currSym}{activeStock.lowPrice != null ? Number(activeStock.lowPrice).toFixed(2) : '--'}</div>
                  </div>
                  <div>
                    <div className="text-muted">CUM. VOLUME</div>
                    <div className="mono font-bold mt-sm">{activeStock.cumulativeVolume?.toLocaleString() || activeStock.volume?.toLocaleString() || '--'}</div>
                  </div>
                </div>
              </div>

              {/* Chart & Conviction Split */}
              <div className="grid-auto grid-auto-fit-xl">
                <InteractiveChart
                  symbol={activeStock.symbol}
                  currentPrice={activeStock.price}
                  currentSma={activeStock.sma}
                  apiBase={apiBase}
                  isUsEquity={isUsEquity}
                />
                <ConvictionScoreGauge snapshot={activeStock} />
              </div>

              {/* Technical Indicators */}
              <div className="terminal-panel p-xl">
                <div className="mb-base">
                  <h3 className="section-title">TECHNICAL INDICATOR SNAPSHOT</h3>
                  <p className="section-subtitle">Rolling mathematical state — O(1) time complexity</p>
                </div>

                <div className="grid-auto grid-metrics">
                  {/* SMA */}
                  <div className="metric-block">
                    <div className="metric-label">20-PERIOD SMA</div>
                    <div className="metric-value">{currSym}{activeStock.sma ? activeStock.sma.toFixed(2) : '--'}</div>
                    {(() => { const b = getSignalBadge(activeStock.signals?.trend); return <span className="badge" style={{ background: b.bg, color: b.color }}>{b.label}</span>; })()}
                  </div>

                  {/* EMA */}
                  <div className="metric-block">
                    <div className="metric-label">20-PERIOD EMA</div>
                    <div className="metric-value">{currSym}{activeStock.ema ? activeStock.ema.toFixed(2) : '--'}</div>
                    <span className="text-secondary" style={{ fontSize: '0.65rem', fontWeight: 700 }}>EXPONENTIAL</span>
                  </div>

                  {/* RSI */}
                  <div className="metric-block">
                    <div className="metric-label">14-PERIOD RSI</div>
                    <div className="metric-value">{activeStock.rsi ? activeStock.rsi.toFixed(1) : '--'}</div>
                    {(() => { const b = getSignalBadge(activeStock.signals?.rsi); return <span className="badge" style={{ background: b.bg, color: b.color }}>{b.label}</span>; })()}
                  </div>

                  {/* Momentum */}
                  <div className="metric-block">
                    <div className="metric-label">10-P MOMENTUM</div>
                    <div className="metric-value">{activeStock.momentum != null ? `${activeStock.momentum > 0 ? '+' : ''}${activeStock.momentum.toFixed(2)}%` : '--'}</div>
                    {(() => { const b = getSignalBadge(activeStock.signals?.momentum); return <span className="badge" style={{ background: b.bg, color: b.color }}>{b.label}</span>; })()}
                  </div>

                  {/* RVOL */}
                  <div className="metric-block">
                    <div className="metric-label">RELATIVE VOLUME</div>
                    <div className="metric-value">{activeStock.relativeVolume ? `${activeStock.relativeVolume.toFixed(2)}x` : '--'}</div>
                    {(() => { const b = getSignalBadge(activeStock.signals?.volume); return <span className="badge" style={{ background: b.bg, color: b.color }}>{b.label}</span>; })()}
                  </div>
                </div>
              </div>
            </div>
          )}

          {/* ═══ TAB: WATCHLIST ═══ */}
          {activeTab === 'WATCHLIST' && (
            <WatchlistManager
              apiBase={apiBase}
              onSelectSymbol={(sym) => {
                setSelectedSymbol(sym);
                setActiveTab('STOCK_DETAIL');
              }}
              currentMarketData={marketData}
            />
          )}

          {/* ═══ TAB: ALERTS ═══ */}
          {activeTab === 'ALERTS' && <AlertsManager apiBase={apiBase} latestAlertEvent={latestAlertEvent} />}

          {/* ═══ TAB: ENGINE HEALTH ═══ */}
          {activeTab === 'ENGINE' && <EngineHealthView apiBase={apiBase} />}
        </main>

        {/* Footer */}
        <footer className="app-footer">
          <div style={{ maxWidth: '840px', margin: '0 auto' }}>
            <p>
              <strong>Academic Decision-Support Disclaimer:</strong> QuantStream is a real-time quantitative streaming
              analytics engine. All conviction scores, trend classifications, momentum metrics, and relative volume
              indications are computed algorithmically for educational decision-support evaluation and do not constitute
              financial advice or automated trade execution.
            </p>
          </div>
        </footer>
      </div>
    </div>
  );
}
