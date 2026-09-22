'use client';

/**
 * ==============================================================================
 * Application Sidebar Navigation (frontend/src/components/Sidebar.jsx)
 * ==============================================================================
 *
 * WHAT IS THIS COMPONENT FOR? (Plain English):
 * This is the left-hand navigation column you see on desktop screens (or slide-out
 * drawer on mobile phones).
 *
 * It contains:
 * 1. THE LOGO & BRAND:
 *    Clicking "QUANTSTREAM" always takes you back home to the main Dashboard.
 * 2. NAVIGATION BUTTONS:
 *    - Dashboard (High-level pulse of the market)
 *    - Scanner (High-speed table of all stocks)
 *    - Stock Detail (Deep-dive into indicators & technical math)
 *    - Watchlist (Your customized favorites)
 *    - Alerts (Automated price & RSI notifications)
 *    - Engine Health (Low-level Kafka, queue, and worker thread metrics)
 * 3. LIVE FOOTER TELEMETRY:
 *    Displays whether the browser is connected to the backend (ONLINE/OFFLINE),
 *    which market mode is active, and how many ticks have been ingested.
 * ==============================================================================
 */

import React from 'react';
import { Activity } from 'lucide-react';

// The navigation menu structure organized into logical categories
const NAV_SECTIONS = [
  {
    label: 'Intelligence Platform',
    items: [
      { id: 'DASHBOARD',     label: 'Dashboard',      icon: 'query_stats' },
      { id: 'SCANNER',       label: 'Scanner',         icon: 'bar_chart' },
      { id: 'STOCK_DETAIL',  label: 'Stock Detail',    icon: 'candlestick_chart' },
      { id: 'WATCHLIST',     label: 'Watchlist',        icon: 'star' },
      { id: 'ALERTS',        label: 'Alerts',           icon: 'notifications_active' },
    ],
  },
  {
    label: 'System',
    items: [
      { id: 'ENGINE', label: 'Engine Health', icon: 'memory' },
    ],
  },
];


export default function Sidebar({
  activeTab,
  onSelectTab,
  connectionStatus,
  totalTicks,
  marketConfig,
  isOpen,
  onClose,
}) {
  const isConnected = connectionStatus === 'CONNECTED';
  const isLive = marketConfig?.mode === 'live';

  return (
    <>
      {/* Mobile overlay */}
      <div
        className={`sidebar-overlay ${isOpen ? 'open' : ''}`}
        onClick={onClose}
      />

      <aside className={`sidebar ${isOpen ? 'open' : ''}`}>
        <div>
          {/* Brand */}
          <div className="sidebar-brand" onClick={() => onSelectTab('DASHBOARD')}>
            <div className="sidebar-brand-icon">
              <Activity size={18} color="#ffffff" />
            </div>
            <div>
              <div className="sidebar-brand-text">QUANTSTREAM</div>
              <div className="sidebar-brand-sub">Institutional</div>
            </div>
          </div>

          {/* Nav Sections */}
          {NAV_SECTIONS.map((section) => (
            <div className="sidebar-section" key={section.label}>
              <div className="sidebar-section-label">{section.label}</div>
              <nav className="sidebar-nav">
                {section.items.map((item) => (
                  <button
                    key={item.id}
                    className={`sidebar-nav-item ${activeTab === item.id ? 'active' : ''}`}
                    onClick={() => {
                      onSelectTab(item.id);
                      if (onClose) onClose();
                    }}
                  >
                    <span className="material-symbols-outlined" style={{ fontSize: '20px' }}>
                      {item.icon}
                    </span>
                    <span>{item.label}</span>
                  </button>
                ))}
              </nav>
            </div>
          ))}
        </div>

        {/* Footer Status */}
        <div className="sidebar-footer">
          <div className="sidebar-status">
            <div className="sidebar-status-row">
              <span className="text-muted">Connection</span>
              <span className="flex-row items-center gap-xs">
                <span className={isConnected ? 'live-dot' : 'offline-dot'} />
                <span
                  className="mono font-bold"
                  style={{
                    fontSize: '0.7rem',
                    color: isConnected ? 'var(--bullish)' : 'var(--bearish)',
                  }}
                >
                  {isConnected ? 'ONLINE' : 'OFFLINE'}
                </span>
              </span>
            </div>

            <div className="sidebar-status-row">
              <span className="text-muted">Data Engine</span>
              <span
                className="mono font-bold"
                style={{
                  fontSize: '0.7rem',
                  color: isLive ? 'var(--bullish)' : 'var(--neutral)',
                }}
              >
                {isLive ? 'LIVE (US)' : 'SIMULATION (NSE)'}
              </span>
            </div>

            <div className="sidebar-status-row">
              <span className="text-muted">Ticks</span>
              <span
                className="mono font-bold"
                style={{ fontSize: '0.7rem', color: 'var(--text-primary)' }}
              >
                {totalTicks ? totalTicks.toLocaleString() : '0'}
              </span>
            </div>
          </div>
        </div>
      </aside>
    </>
  );
}
