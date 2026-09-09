'use client';

import React, { useState } from 'react';
import { Zap, Activity, CheckCircle, Terminal, X } from 'lucide-react';

export default function MarketModeSelector({ marketConfig, connectionStatus, trackedCount }) {
  const [showSwitchModal, setShowSwitchModal] = useState(false);
  const [requestedMode, setRequestedMode] = useState(null);

  const activeMode = marketConfig?.mode?.toLowerCase() || 'simulation';
  const isLiveActive = activeMode === 'live';
  const providerStatus = marketConfig?.status || 'UNKNOWN';

  // Live status badge styling
  const getLiveBadge = () => {
    if (!isLiveActive) return { label: 'AVAILABLE ON RESTART', color: 'var(--text-muted)', dotClass: 'unconfigured-dot' };
    if (providerStatus === 'CONNECTED' && connectionStatus === 'CONNECTED') return { label: 'CONNECTED (REAL-TIME)', color: 'var(--bullish)', dotClass: 'live-dot' };
    if (providerStatus === 'UNCONFIGURED') return { label: 'KEY UNCONFIGURED', color: 'var(--neutral)', dotClass: 'unconfigured-dot' };
    if (connectionStatus === 'CONNECTING') return { label: 'CONNECTING...', color: 'var(--neutral)', dotClass: 'connecting-dot' };
    return { label: 'DISCONNECTED / RETRYING', color: 'var(--bearish)', dotClass: 'offline-dot' };
  };

  const getSimBadge = () => {
    if (isLiveActive) return { label: 'READY ON RESTART', color: 'var(--text-muted)', dotClass: 'unconfigured-dot' };
    return { label: 'ACTIVE (DETERMINISTIC)', color: 'var(--neutral)', dotClass: 'live-dot' };
  };

  const liveBadge = getLiveBadge();
  const simBadge = getSimBadge();

  const handleSelectMode = (mode) => {
    if (mode === activeMode) return;
    setRequestedMode(mode);
    setShowSwitchModal(true);
  };

  return (
    <div className="terminal-panel p-xl mb-xl">
      {/* Header Banner */}
      <div className="flex-row justify-between items-start flex-wrap gap-md mb-base">
        <div>
          <div className="flex-row items-center gap-sm">
            <span className="label-caps text-accent">DATA SOURCE UX</span>
            <span className="text-muted" style={{ fontSize: '0.75rem' }}>• Ingestion Engine Architecture</span>
          </div>
          <h2 className="section-title mt-xs">Market Data Source &amp; Configuration</h2>
        </div>

        <div className="badge-pill flex-row items-center gap-sm" style={{ background: 'rgba(255,255,255,0.04)', border: '1px solid var(--border-color)', padding: '6px 14px' }}>
          <span className="live-dot" style={{ width: '6px', height: '6px' }} />
          <span className={`mono font-bold ${isLiveActive ? 'text-bullish' : 'text-neutral'}`} style={{ fontSize: '0.8rem' }}>
            {isLiveActive ? '● STREAMING DATA SOURCE: LIVE MARKET • US EQUITIES' : '● STREAMING DATA SOURCE: SIMULATION • NSE EQUITIES'}
          </span>
        </div>
      </div>

      {/* Two Large Selectable Cards */}
      <div className="grid-auto grid-2">
        {/* Card 1: LIVE MARKET */}
        <div
          onClick={() => handleSelectMode('live')}
          className={`mode-card mode-card--live ${isLiveActive ? 'mode-card--active' : ''}`}
        >
          {isLiveActive && (
            <div className="mode-card-active-badge badge badge-bullish">
              <CheckCircle size={12} /> ACTIVE
            </div>
          )}

          <div className="flex-row items-center gap-sm mb-sm">
            <div className="mode-card-icon" style={{ background: isLiveActive ? 'rgba(16, 185, 129, 0.15)' : 'rgba(255,255,255,0.05)' }}>
              <Zap size={18} color={isLiveActive ? 'var(--bullish)' : 'var(--text-muted)'} />
            </div>
            <div>
              <h3 className="section-title" style={{ fontSize: '1rem' }}>LIVE MARKET</h3>
              <p className="section-subtitle">Finnhub WebSocket • US Equities (USD)</p>
            </div>
          </div>

          <p className="text-muted mb-base" style={{ fontSize: '0.8rem', lineHeight: '1.4' }}>
            Real-time trade streaming directly from Finnhub market data WebSocket. Ingests tick by tick into Kafka.
          </p>

          <div className="flex-row justify-between items-center pt-md" style={{ borderTop: '1px solid var(--border-subtle)' }}>
            <div className="flex-row items-center gap-xs">
              <span className={liveBadge.dotClass} style={{ width: '6px', height: '6px' }} />
              <span className="mono font-bold" style={{ fontSize: '0.75rem', color: liveBadge.color }}>
                {liveBadge.label}
              </span>
            </div>
            {!isLiveActive && (
              <span className="font-bold text-accent" style={{ fontSize: '0.75rem' }}>Switch to Live &rarr;</span>
            )}
          </div>
        </div>

        {/* Card 2: SIMULATION */}
        <div
          onClick={() => handleSelectMode('simulation')}
          className={`mode-card mode-card--sim ${!isLiveActive ? 'mode-card--active' : ''}`}
        >
          {!isLiveActive && (
            <div className="mode-card-active-badge badge badge-neutral">
              <CheckCircle size={12} /> ACTIVE
            </div>
          )}

          <div className="flex-row items-center gap-sm mb-sm">
            <div className="mode-card-icon" style={{ background: !isLiveActive ? 'rgba(245, 158, 11, 0.15)' : 'rgba(255,255,255,0.05)' }}>
              <Activity size={18} color={!isLiveActive ? 'var(--neutral)' : 'var(--text-muted)'} />
            </div>
            <div>
              <h3 className="section-title" style={{ fontSize: '1rem' }}>SIMULATION</h3>
              <p className="section-subtitle">Deterministic Synthetic Feed • Indian Equities (₹)</p>
            </div>
          </div>

          <p className="text-muted mb-base" style={{ fontSize: '0.8rem', lineHeight: '1.4' }}>
            Reproducible pseudo-random walk simulation with geometric Brownian motion. Zero external API keys needed.
          </p>

          <div className="flex-row justify-between items-center pt-md" style={{ borderTop: '1px solid var(--border-subtle)' }}>
            <div className="flex-row items-center gap-xs">
              <span className={simBadge.dotClass} style={{ width: '6px', height: '6px' }} />
              <span className="mono font-bold" style={{ fontSize: '0.75rem', color: simBadge.color }}>
                {simBadge.label}
              </span>
            </div>
            {isLiveActive && (
              <span className="font-bold text-neutral" style={{ fontSize: '0.75rem' }}>Switch to Demo &rarr;</span>
            )}
          </div>
        </div>
      </div>

      {/* Switch Instructions Modal */}
      {showSwitchModal && (
        <div className="modal-overlay">
          <div className="modal-content">
            <div className="flex-row justify-between items-center mb-base">
              <div className="flex-row items-center gap-sm">
                <Terminal size={18} color="var(--accent-blue)" />
                <h3 className="section-title">Switch Market Data Mode</h3>
              </div>
              <button className="btn-ghost" onClick={() => setShowSwitchModal(false)}>
                <X size={18} />
              </button>
            </div>

            <div className="modal-notice mb-base">
              <strong>Notice:</strong> QuantStream isolates ingestion beans using Spring Boot conditional properties. Switching between Live Finnhub and Simulation mode is a startup launch configuration and requires restarting the backend.
            </div>

            <p className="text-secondary mb-sm" style={{ fontSize: '0.85rem' }}>
              To activate <strong>{requestedMode === 'live' ? 'Live Finnhub Mode' : 'Simulation Mode'}</strong>, restart the Spring Boot backend:
            </p>

            <div className="modal-code mb-lg">
              {requestedMode === 'live' ? (
                <>
                  $env:MARKET_DATA_MODE = "live"<br />
                  $env:FINNHUB_API_KEY = "your_finnhub_key"<br />
                  .\mvnw.cmd spring-boot:run
                </>
              ) : (
                <>
                  $env:MARKET_DATA_MODE = "simulation"<br />
                  .\mvnw.cmd spring-boot:run
                </>
              )}
            </div>

            <div className="flex-row justify-end gap-sm">
              <button className="btn-secondary" onClick={() => setShowSwitchModal(false)}>
                Understood
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
