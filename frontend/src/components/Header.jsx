'use client';

/**
 * ==============================================================================
 * Top Navigation Bar (frontend/src/components/Header.jsx)
 * ==============================================================================
 *
 * WHAT IS THIS COMPONENT FOR? (Plain English):
 * This is the header bar that sits at the very top of your browser window.
 *
 * KEY FEATURES:
 * 1. QUICK STOCK SEARCH (with ⌘K / Ctrl+K shortcut):
 *    Start typing any company name or ticker (like "Apple", "AAPL", "Reliance"),
 *    and a dropdown appears instantly with live prices. Clicking one jumps straight
 *    to that stock's detailed analysis page!
 * 2. LIVE CONNECTION STATUS BADGE:
 *    - Green "● STREAMING": WebSocket is connected and receiving live data!
 *    - Red "○ RECONNECTING": Network dropped, system is reconnecting.
 * 3. DATA SOURCE BADGE:
 *    Clearly tells the user whether they are watching real New York Stock Exchange
 *    data (Finnhub) or simulated Indian NSE equity data.
 * 4. TICK COUNTER:
 *    A running odometer showing how many thousands of price updates QuantStream
 *    has ingested since this session started.
 * ==============================================================================
 */

import React, { useState, useMemo, useRef, useEffect } from 'react';
import { Search, Menu, X } from 'lucide-react';

export default function Header({
  connectionStatus,
  totalTicks,
  marketConfig,
  allInstruments = [],
  apiBase = '',
  onSelectSymbol,
  onSelectTab,
  onToggleSidebar,
}) {
  // Text currently typed into the search bar
  const [searchQuery, setSearchQuery] = useState('');
  // Whether the search results dropdown menu is visible
  const [showDropdown, setShowDropdown] = useState(false);
  // Full master list of symbols loaded from `/api/instruments/active`
  const [registry, setRegistry] = useState([]);
  // Reference to the search box container (used to detect clicks outside to close dropdown)
  const searchRef = useRef(null);

  const isConnected = connectionStatus === 'CONNECTED';
  const isLive = marketConfig?.mode === 'live';


  useEffect(() => {
    fetch(`${apiBase || 'http://localhost:8080'}/api/instruments/active`)
      .then((res) => (res.ok ? res.json() : []))
      .then((data) => {
        if (Array.isArray(data) && data.length > 0) {
          setRegistry(data);
        }
      })
      .catch(() => {});
  }, [apiBase]);

  // Combine live market data with full instrument registry
  const searchUniverse = useMemo(() => {
    const liveMap = new Map(allInstruments.map((s) => [s.symbol, s]));
    if (!registry || registry.length === 0) return allInstruments;
    return registry.map((item) => {
      const live = liveMap.get(item.symbol);
      return {
        ...item,
        price: live?.price ?? null,
        priceChange: live?.priceChange ?? null,
        priceChangePercent: live?.priceChangePercent ?? null,
      };
    });
  }, [allInstruments, registry]);

  // Filter instruments by search query
  const filteredInstruments = useMemo(() => {
    if (!searchQuery.trim()) return [];
    const q = searchQuery.trim().toUpperCase();
    return searchUniverse
      .filter(
        (s) =>
          s.symbol?.toUpperCase().includes(q) ||
          s.companyName?.toUpperCase().includes(q)
      )
      .slice(0, 10);
  }, [searchQuery, searchUniverse]);

  // Close dropdown on outside click
  useEffect(() => {
    const handler = (e) => {
      if (searchRef.current && !searchRef.current.contains(e.target)) {
        setShowDropdown(false);
      }
    };
    document.addEventListener('mousedown', handler);
    return () => document.removeEventListener('mousedown', handler);
  }, []);

  // ⌘K keyboard shortcut
  useEffect(() => {
    const handler = (e) => {
      if ((e.metaKey || e.ctrlKey) && e.key === 'k') {
        e.preventDefault();
        const input = searchRef.current?.querySelector('input');
        if (input) {
          input.focus();
          setShowDropdown(true);
        }
      }
    };
    document.addEventListener('keydown', handler);
    return () => document.removeEventListener('keydown', handler);
  }, []);

  return (
    <header className="header">
      <div className="header-left">
        {/* Mobile sidebar toggle */}
        <button className="sidebar-toggle" onClick={onToggleSidebar}>
          <Menu size={22} />
        </button>

        {/* Search */}
        <div ref={searchRef} style={{ position: 'relative' }}>
          <div className="search-box" style={{ minWidth: '220px', maxWidth: '340px' }}>
            <Search size={15} color="var(--text-muted)" />
            <input
              type="text"
              placeholder="Search instruments..."
              value={searchQuery}
              onChange={(e) => {
                setSearchQuery(e.target.value);
                setShowDropdown(true);
              }}
              onFocus={() => setShowDropdown(true)}
            />
            <span className="search-kbd">⌘K</span>
          </div>

          {/* Search Dropdown */}
          {showDropdown && filteredInstruments.length > 0 && (
            <div className="search-dropdown">
              {filteredInstruments.map((inst) => (
                <div
                  key={inst.symbol}
                  className="search-dropdown-item"
                  onClick={() => {
                    onSelectSymbol(inst.symbol);
                    onSelectTab('STOCK_DETAIL');
                    setSearchQuery('');
                    setShowDropdown(false);
                  }}
                >
                  <div className="flex-row items-center gap-sm">
                    <span
                      className="mono font-extrabold text-accent"
                      style={{ fontSize: '0.82rem' }}
                    >
                      {inst.symbol}
                    </span>
                    <span className="text-secondary" style={{ fontSize: '0.72rem' }}>
                      {inst.companyName}
                    </span>
                  </div>
                  <div className="flex-row items-center gap-sm">
                    <span className="mono font-bold" style={{ fontSize: '0.8rem' }}>
                      {inst.price != null ? Number(inst.price).toFixed(2) : '--'}
                    </span>
                    <span
                      className="mono font-bold"
                      style={{
                        fontSize: '0.72rem',
                        color: (inst.priceChangePercent ?? 0) >= 0 ? 'var(--bullish)' : 'var(--bearish)',
                      }}
                    >
                      {(inst.priceChangePercent ?? 0) >= 0 ? '+' : ''}
                      {inst.priceChangePercent?.toFixed(2) || '0.00'}%
                    </span>
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>
      </div>

      <div className="header-right">
        {/* Truthful Connection Status */}
        <div
          className="flex-row items-center gap-xs badge-pill"
          style={{
            background: isConnected ? 'var(--bullish-bg)' : 'var(--bearish-bg)',
            border: `1px solid ${isConnected ? 'var(--bullish-border)' : 'var(--bearish-border)'}`,
            padding: '4px 12px',
          }}
        >
          <span className={isConnected ? 'live-dot' : 'offline-dot'} style={{ width: '6px', height: '6px' }} />
          <span
            className="mono font-bold"
            style={{
              fontSize: '0.68rem',
              color: isConnected ? 'var(--bullish)' : 'var(--bearish)',
            }}
          >
            {isConnected ? '● STREAMING' : '○ RECONNECTING'}
          </span>
        </div>

        {/* Truthful Data Source Status */}
        <div
          className="flex-row items-center gap-xs badge-pill"
          style={{
            background: 'rgba(255,255,255,0.04)',
            border: '1px solid var(--border-color)',
            padding: '4px 12px',
          }}
        >
          <span className="mono" style={{ fontSize: '0.68rem', color: 'var(--text-muted)' }}>DATA SOURCE:</span>
          <span
            className="mono font-bold"
            style={{ fontSize: '0.68rem', color: isLive ? 'var(--bullish)' : 'var(--neutral)' }}
          >
            {isLive ? 'LIVE MARKET • US EQUITIES' : 'SIMULATION • NSE EQUITIES'}
          </span>
        </div>

        {/* Tick Counter */}
        <div
          className="flex-row items-center gap-xs"
          style={{ fontSize: '0.72rem', color: 'var(--text-muted)' }}
        >
          <span>Ticks:</span>
          <span className="mono font-bold text-primary">
            {totalTicks ? totalTicks.toLocaleString() : '0'}
          </span>
        </div>
      </div>
    </header>
  );
}
