'use client';

/**
 * ==============================================================================
 * High-Speed Financial Stock Scanner (frontend/src/components/ScannerTable.jsx)
 * ==============================================================================
 *
 * WHAT IS THIS COMPONENT FOR? (Plain English):
 * Professional quant traders don't look at one stock at a time. They look at a
 * "Scanner" (or Screener) — a dense, real-time spreadsheet that continuously sorts,
 * filters, and monitors hundreds of stocks at once!
 *
 * KEY FEATURES IN THIS SCANNER:
 * 1. MULTI-FACTOR FILTERING:
 *    - By Conviction Tier: Instantly isolate 'VERY STRONG' bullish stocks or 'WEAK' stocks.
 *    - By Sector: Filter to only Technology, Financials, Healthcare, Energy, etc.
 *    - By Minimum Score: A slider to find stocks with conviction > 75.
 *    - By Search Query: Instant symbol or company search.
 * 2. MULTI-COLUMN SORTING:
 *    Click any column header (Price, Change %, RSI, SMA, RVOL, Conviction) to sort
 *    highest-to-lowest or lowest-to-highest.
 * 3. VIRTUALIZED SCROLLING (`react-window`):
 *    If our universe contains 240 stocks, drawing 240 complex rows with glowing badges
 *    all at once could slow down your browser. `react-window` is a genius optimization:
 *    it only renders the 10-15 rows currently visible on your screen, recycling DOM
 *    nodes as you scroll for silky-smooth 60 frames-per-second performance!
 * 4. ONE-CLICK EXPLAINABILITY MODAL:
 *    Clicking any stock's conviction badge pops up the 4-Factor Speedometer gauge
 *    and mathematical rationale without leaving the scanner.
 * ==============================================================================
 */

import React, { useState, useMemo, useEffect } from 'react';
import { ArrowUp, ArrowDown, Search, SlidersHorizontal, Layers, X, ArrowRight } from 'lucide-react';
import { List } from 'react-window';
import ConvictionScoreGauge from './ConvictionScoreGauge';
import { isUsEquityStock, currencySymbol, exchangeTag } from '../lib/marketUtils';

export default function ScannerTable({
  marketData,
  onSelectSymbol,
  selectedSymbol,
  marketConfig,
  apiBase,
}) {
  // Filter states
  const [filterCategory, setFilterCategory] = useState('ALL');
  const [sectorFilter, setSectorFilter] = useState('ALL');
  const [minScore, setMinScore] = useState(0);
  const [searchQuery, setSearchQuery] = useState('');
  
  // Sort states (default: highest conviction score first)
  const [sortField, setSortField] = useState('convictionScore');
  const [sortAsc, setSortAsc] = useState(false);
  
  // Modal state for quick factor breakdown preview
  const [activeModalStock, setActiveModalStock] = useState(null);
  // Master list of active instruments loaded from the backend
  const [instruments, setInstruments] = useState([]);


  const endpoint = apiBase || process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080';

  // Load active instruments for dynamic sector metadata
  useEffect(() => {
    let mounted = true;
    async function loadInstruments() {
      try {
        const res = await fetch(`${endpoint}/api/instruments/active`);
        if (res.ok) {
          const list = await res.json();
          if (mounted && Array.isArray(list)) {
            setInstruments(list);
          }
        }
      } catch (e) {
        // Fallback silently if offline
      }
    }
    loadInstruments();
    return () => {
      mounted = false;
    };
  }, [endpoint]);

  // Symbol to Sector mapping
  const sectorBySymbol = useMemo(() => {
    const map = {};
    instruments.forEach((inst) => {
      if (inst.symbol && inst.sector) {
        map[inst.symbol.toUpperCase()] = inst.sector;
      }
    });
    return map;
  }, [instruments]);

  const stocks = useMemo(() => Object.values(marketData || {}), [marketData]);

  // Dynamically extract unique sectors from instruments and live stocks (never hardcoded)
  const availableSectors = useMemo(() => {
    const set = new Set();
    instruments.forEach((inst) => {
      if (inst.sector && inst.sector.trim()) {
        set.add(inst.sector.trim());
      }
    });
    stocks.forEach((s) => {
      if (s.sector && s.sector.trim()) {
        set.add(s.sector.trim());
      }
      const symSec = sectorBySymbol[s.symbol?.toUpperCase()];
      if (symSec && symSec.trim()) {
        set.add(symSec.trim());
      }
    });
    return Array.from(set).sort();
  }, [instruments, stocks, sectorBySymbol]);

  // Filtering
  const filteredStocks = useMemo(() => {
    return stocks.filter((stock) => {
      // Category filter
      if (filterCategory !== 'ALL' && stock.scoreCategory !== filterCategory) return false;

      // Sector filter
      if (sectorFilter !== 'ALL') {
        const stockSector = stock.sector || sectorBySymbol[stock.symbol?.toUpperCase()];
        if (stockSector !== sectorFilter) return false;
      }

      // Min score
      const score = stock.convictionScore ?? 50;
      if (score < minScore) return false;

      // Search
      if (searchQuery.trim()) {
        const q = searchQuery.trim().toUpperCase();
        const symMatch = stock.symbol && stock.symbol.toUpperCase().includes(q);
        const nameMatch = stock.companyName && stock.companyName.toUpperCase().includes(q);
        if (!symMatch && !nameMatch) return false;
      }

      return true;
    });
  }, [stocks, filterCategory, sectorFilter, minScore, searchQuery, sectorBySymbol]);

  // Sorting
  const sortedStocks = useMemo(() => {
    const list = [...filteredStocks];
    list.sort((a, b) => {
      let valA = a[sortField] ?? 0;
      let valB = b[sortField] ?? 0;
      if (sortField === 'convictionScore') {
        valA = a.convictionScore ?? 50;
        valB = b.convictionScore ?? 50;
      }
      return sortAsc ? valA - valB : valB - valA;
    });
    return list;
  }, [filteredStocks, sortField, sortAsc]);

  const handleSort = (field) => {
    if (sortField === field) {
      setSortAsc(!sortAsc);
    } else {
      setSortField(field);
      setSortAsc(false);
    }
  };

  const getCategoryColor = (cat) => {
    switch (cat) {
      case 'VERY_STRONG': return 'var(--score-very-strong)';
      case 'STRONG':      return 'var(--score-strong)';
      case 'NEUTRAL':     return 'var(--score-neutral)';
      case 'WEAK':        return 'var(--score-weak)';
      case 'VERY_WEAK':   return 'var(--score-very-weak)';
      default:            return 'var(--score-neutral)';
    }
  };

  // Quantitative Summary metrics
  const strongCount = stocks.filter((s) => s.scoreCategory === 'STRONG' || s.scoreCategory === 'VERY_STRONG').length;
  const neutralCount = stocks.filter((s) => s.scoreCategory === 'NEUTRAL').length;
  const weakCount = stocks.filter((s) => s.scoreCategory === 'WEAK' || s.scoreCategory === 'VERY_WEAK').length;
  const avgScore = stocks.length > 0
    ? stocks.reduce((acc, curr) => acc + (curr.convictionScore ?? 50), 0) / stocks.length
    : 50.0;

  // Virtualized Row Renderer
  const Row = ({
    index,
    style,
    ariaAttributes,
    stocks,
    onSelectStock,
    selectedSym,
    config,
    secBySym,
  }) => {
    const stockList = stocks || sortedStocks;
    const stock = stockList[index];
    if (!stock) return null;

    const isSelected = (selectedSym !== undefined ? selectedSym : selectedSymbol) === stock.symbol;
    const isUp = (stock.priceChangePercent ?? 0) >= 0;
    const cur = currencySymbol(stock, config || marketConfig);
    const tag = exchangeTag(stock, config || marketConfig);
    const score = stock.convictionScore != null ? Number(stock.convictionScore) : 50.0;
    const category = stock.scoreCategory || 'NEUTRAL';
    const scoreColor = getCategoryColor(category);
    const sectorMap = secBySym || sectorBySymbol;
    const sectorName = stock.sector || sectorMap[stock.symbol?.toUpperCase()] || '—';

    return (
      <div
        {...ariaAttributes}
        style={{ ...style, minWidth: '1020px', boxSizing: 'border-box' }}
        className={`virtual-row ${isSelected ? 'selected' : ''}`}
        onClick={() => (onSelectStock || setActiveModalStock)(stock)}
      >
        <div style={{ width: '45px', flexShrink: 0 }} className="mono font-bold text-muted">
          #{index + 1}
        </div>

        <div style={{ width: '180px', flexShrink: 0 }}>
          <div className="flex-row items-center gap-xs">
            <span className="mono font-extrabold text-primary">{stock.symbol}</span>
            <span className="badge-exchange">{tag}</span>
          </div>
          <div className="text-secondary truncate" style={{ fontSize: '0.7rem', maxWidth: '170px' }}>
            {stock.companyName}
          </div>
        </div>

        <div style={{ width: '130px', flexShrink: 0 }} className="truncate">
          <span
            className="badge"
            style={{
              fontSize: '0.64rem',
              background: 'rgba(255, 255, 255, 0.04)',
              color: 'var(--text-secondary)',
              border: '1px solid rgba(255, 255, 255, 0.07)',
            }}
          >
            {sectorName}
          </span>
        </div>

        <div style={{ width: '105px', flexShrink: 0, textAlign: 'right' }} className="mono font-bold">
          {cur}{stock.price != null ? Number(stock.price).toFixed(2) : '--'}
        </div>

        <div style={{ width: '100px', flexShrink: 0, textAlign: 'right' }} className={`mono font-bold ${isUp ? 'text-bullish' : 'text-bearish'}`}>
          {isUp ? '+' : ''}{stock.priceChangePercent != null ? stock.priceChangePercent.toFixed(2) : '0.00'}%
        </div>

        <div style={{ width: '80px', flexShrink: 0, textAlign: 'right' }} className="mono">
          {stock.rsi != null && stock.rsi > 0 ? stock.rsi.toFixed(1) : '--'}
        </div>

        <div style={{ width: '95px', flexShrink: 0, textAlign: 'right' }} className={`mono ${stock.momentum >= 0 ? 'text-bullish' : 'text-bearish'}`}>
          {stock.momentum != null ? `${stock.momentum > 0 ? '+' : ''}${stock.momentum.toFixed(2)}%` : '--'}
        </div>

        <div style={{ width: '80px', flexShrink: 0, textAlign: 'right' }} className="mono">
          {stock.relativeVolume != null && stock.relativeVolume > 0 ? `${stock.relativeVolume.toFixed(2)}x` : '--'}
        </div>

        <div style={{ width: '155px', flexShrink: 0, padding: '0 8px' }}>
          <div className="flex-row items-center gap-sm">
            <span className="mono font-extrabold" style={{ fontSize: '0.92rem', color: scoreColor }}>
              {score.toFixed(1)}
            </span>
            <div className="score-bar" style={{ flex: 1 }}>
              <div
                className="score-bar-fill"
                style={{ width: `${Math.min(100, Math.max(0, score))}%`, backgroundColor: scoreColor }}
              />
            </div>
          </div>
        </div>

        <div style={{ width: '110px', flexShrink: 0 }}>
          <span className="badge" style={{ color: scoreColor, background: `${scoreColor}18`, border: `1px solid ${scoreColor}30`, fontSize: '0.66rem' }}>
            {category.replace('_', ' ')}
          </span>
        </div>

        <div style={{ width: '40px', flexShrink: 0, textAlign: 'center' }}>
          <span className="text-muted" title="View Factor Breakdown" style={{ fontSize: '0.78rem' }}>
            ▸
          </span>
        </div>
      </div>
    );
  };

  const rowProps = useMemo(() => ({
    stocks: sortedStocks,
    onSelectStock: setActiveModalStock,
    selectedSym: selectedSymbol,
    config: marketConfig,
    secBySym: sectorBySymbol,
  }), [sortedStocks, selectedSymbol, marketConfig, sectorBySymbol]);

  return (
    <div className="flex-col gap-xl">
      {/* Quantitative Summary Panel */}
      <div className="terminal-panel p-xl flex-row items-center justify-between flex-wrap gap-xl">
        <div>
          <div className="stat-card-label">TRACKED INSTRUMENTS</div>
          <div className="stat-card-value">{stocks.length}</div>
        </div>
        <div className="flex-row gap-xl">
          <div>
            <div className="stat-card-label">STRONG</div>
            <div className="stat-card-value text-bullish">{strongCount}</div>
          </div>
          <div>
            <div className="stat-card-label">NEUTRAL</div>
            <div className="stat-card-value text-neutral">{neutralCount}</div>
          </div>
          <div>
            <div className="stat-card-label">WEAK</div>
            <div className="stat-card-value text-bearish">{weakCount}</div>
          </div>
        </div>
        <div>
          <div className="stat-card-label">AVG CONVICTION SCORE</div>
          <div className="stat-card-value text-accent">{avgScore.toFixed(1)}</div>
        </div>
      </div>

      <div className="terminal-panel p-xl">
        {/* Scanner Header & Controls */}
        <div className="flex-row justify-between items-start flex-wrap gap-base mb-lg">
          <div>
            <h2 className="section-title flex-row items-center gap-xs">
              <SlidersHorizontal size={18} color="var(--accent-blue)" />
              QUANTITATIVE SCANNER
            </h2>
            <p className="section-subtitle">
              Virtualized real-time ranking ({sortedStocks.length} matching) • Click any row for 4-factor breakdown
            </p>
          </div>

          {/* Search, Sector Filter & Min Score */}
          <div className="flex-row items-center gap-md flex-wrap">
            <div className="search-box">
              <Search size={14} color="var(--text-muted)" />
              <input
                type="text"
                placeholder="Filter ticker..."
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
                style={{ width: '110px' }}
              />
            </div>

            {/* Dynamic Sector Filter Dropdown */}
            <div className="search-box" style={{ padding: '4px 8px' }}>
              <Layers size={14} color="var(--text-muted)" />
              <select
                value={sectorFilter}
                onChange={(e) => setSectorFilter(e.target.value)}
                style={{
                  background: 'transparent',
                  color: 'var(--text-primary)',
                  border: 'none',
                  outline: 'none',
                  fontSize: '0.78rem',
                  fontFamily: 'inherit',
                  cursor: 'pointer',
                  maxWidth: '150px'
                }}
              >
                <option value="ALL" style={{ background: '#131722', color: '#e5e7eb' }}>
                  All Sectors ({availableSectors.length})
                </option>
                {availableSectors.map((sec) => (
                  <option key={sec} value={sec} style={{ background: '#131722', color: '#e5e7eb' }}>
                    {sec}
                  </option>
                ))}
              </select>
            </div>

            <div className="search-box">
              <span style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>Min Score:</span>
              <input
                type="range"
                min="0"
                max="90"
                step="5"
                value={minScore}
                onChange={(e) => setMinScore(Number(e.target.value))}
                style={{ width: '70px', accentColor: 'var(--accent-blue)', cursor: 'pointer' }}
              />
              <span className="mono font-bold text-primary">{minScore}</span>
            </div>
          </div>
        </div>

        {/* Category Pills */}
        <div className="flex-row gap-xs flex-wrap mb-lg">
          {['ALL', 'VERY_STRONG', 'STRONG', 'NEUTRAL', 'WEAK', 'VERY_WEAK'].map((cat) => {
            const isSelected = filterCategory === cat;
            return (
              <button
                key={cat}
                type="button"
                className={`filter-pill ${isSelected ? 'active' : ''}`}
                onClick={() => setFilterCategory(cat)}
              >
                {cat.replace('_', ' ')}
              </button>
            );
          })}
        </div>

        {/* Virtualized Quantitative Table */}
        <div style={{ overflowX: 'auto', width: '100%' }}>
          <div style={{ minWidth: '1020px' }}>
            {/* Table Header */}
            <div className="virtual-header">
              <div style={{ width: '45px', flexShrink: 0 }}>RANK</div>
              <div style={{ width: '180px', flexShrink: 0 }}>SYMBOL</div>
              <div style={{ width: '130px', flexShrink: 0 }}>SECTOR</div>
              <div
                style={{ width: '105px', flexShrink: 0, textAlign: 'right' }}
                className="sortable"
                onClick={() => handleSort('price')}
              >
                PRICE {sortField === 'price' && (sortAsc ? <ArrowUp size={12} /> : <ArrowDown size={12} />)}
              </div>
              <div
                style={{ width: '100px', flexShrink: 0, textAlign: 'right' }}
                className="sortable"
                onClick={() => handleSort('priceChangePercent')}
              >
                CHANGE {sortField === 'priceChangePercent' && (sortAsc ? <ArrowUp size={12} /> : <ArrowDown size={12} />)}
              </div>
              <div
                style={{ width: '80px', flexShrink: 0, textAlign: 'right' }}
                className="sortable"
                onClick={() => handleSort('rsi')}
              >
                RSI (14) {sortField === 'rsi' && (sortAsc ? <ArrowUp size={12} /> : <ArrowDown size={12} />)}
              </div>
              <div style={{ width: '95px', flexShrink: 0, textAlign: 'right' }}>MOMENTUM</div>
              <div style={{ width: '80px', flexShrink: 0, textAlign: 'right' }}>RVOL</div>
              <div
                style={{ width: '155px', flexShrink: 0, padding: '0 8px' }}
                className="sortable"
                onClick={() => handleSort('convictionScore')}
              >
                CONVICTION {sortField === 'convictionScore' && (sortAsc ? <ArrowUp size={12} /> : <ArrowDown size={12} />)}
              </div>
              <div style={{ width: '110px', flexShrink: 0 }}>SIGNAL RATING</div>
              <div style={{ width: '40px', flexShrink: 0, textAlign: 'center' }}>DETAIL</div>
            </div>

            {/* Virtualized Rows or Empty State */}
            {sortedStocks.length === 0 ? (
              <div className="data-table-empty">
                No equities match the active filter criteria.
              </div>
            ) : (
              <List
                rowCount={sortedStocks.length}
                rowHeight={54}
                rowComponent={Row}
                rowProps={rowProps}
                style={{ height: 560, width: '100%' }}
              />
            )}
          </div>
        </div>
      </div>

      {/* Row Click: Conviction Score Factor Breakdown Modal */}
      {activeModalStock && (
        <div
          className="modal-overlay"
          onClick={() => setActiveModalStock(null)}
          style={{ zIndex: 1000 }}
        >
          <div
            className="modal-content"
            style={{ maxWidth: '620px', width: '92%', maxHeight: '90vh', overflowY: 'auto' }}
            onClick={(e) => e.stopPropagation()}
          >
            <div className="flex-row justify-between items-center mb-base">
              <div className="flex-row items-center gap-sm">
                <span className="mono font-extrabold text-primary" style={{ fontSize: '1.25rem' }}>
                  {activeModalStock.symbol}
                </span>
                <span className="badge-exchange">{exchangeTag(activeModalStock, marketConfig)}</span>
                <span className="text-secondary" style={{ fontSize: '0.82rem' }}>
                  {activeModalStock.companyName}
                </span>
              </div>
              <button
                className="btn-ghost"
                onClick={() => setActiveModalStock(null)}
                style={{ padding: '4px 8px' }}
              >
                <X size={18} />
              </button>
            </div>

            {/* Sector + Quick Metrics Row */}
            <div
              className="flex-row items-center justify-between p-sm mb-base"
              style={{
                background: 'rgba(255, 255, 255, 0.02)',
                borderRadius: '4px',
                border: '1px solid var(--border-subtle)',
                fontSize: '0.78rem'
              }}
            >
              <div>
                <span className="text-muted">SECTOR: </span>
                <span className="text-primary font-bold">
                  {activeModalStock.sector || sectorBySymbol[activeModalStock.symbol?.toUpperCase()] || 'General Equity'}
                </span>
              </div>
              <div className="flex-row items-center gap-md">
                <div>
                  <span className="text-muted">PRICE: </span>
                  <span className="mono font-bold text-primary">
                    {currencySymbol(activeModalStock, marketConfig)}
                    {activeModalStock.price != null ? Number(activeModalStock.price).toFixed(2) : '--'}
                  </span>
                </div>
                <div>
                  <span className="text-muted">CHANGE: </span>
                  <span
                    className={`mono font-bold ${(activeModalStock.priceChangePercent ?? 0) >= 0 ? 'text-bullish' : 'text-bearish'}`}
                  >
                    {(activeModalStock.priceChangePercent ?? 0) >= 0 ? '+' : ''}
                    {activeModalStock.priceChangePercent != null ? activeModalStock.priceChangePercent.toFixed(2) : '0.00'}%
                  </span>
                </div>
              </div>
            </div>

            {/* Extended Conviction Score Gauge with Factor Detail */}
            <ConvictionScoreGauge
              snapshot={activeModalStock}
              showBreakdownInitial={true}
            />

            {/* Modal Actions */}
            <div className="flex-row justify-between items-center mt-base pt-sm" style={{ borderTop: '1px solid var(--border-subtle)' }}>
              <span className="text-muted" style={{ fontSize: '0.72rem' }}>
                Ticks evaluated autonomously in real time
              </span>
              <div className="flex-row items-center gap-sm">
                <button
                  type="button"
                  className="btn-secondary"
                  onClick={() => setActiveModalStock(null)}
                >
                  Close
                </button>
                {onSelectSymbol && (
                  <button
                    type="button"
                    className="btn-primary flex-row items-center gap-xs"
                    onClick={() => {
                      onSelectSymbol(activeModalStock.symbol);
                      setActiveModalStock(null);
                    }}
                  >
                    Go to Full Analysis <ArrowRight size={14} />
                  </button>
                )}
              </div>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
