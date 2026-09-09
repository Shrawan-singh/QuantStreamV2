'use client';

import React from 'react';
import { Activity } from 'lucide-react';

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
