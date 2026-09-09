'use client';

import React, { useState, useMemo } from 'react';
import { ArrowUp, ArrowDown, Search, SlidersHorizontal } from 'lucide-react';
import { isUsEquityStock, currencySymbol, exchangeTag } from '../lib/marketUtils';

export default function ScannerTable({ marketData, onSelectSymbol, selectedSymbol, marketConfig }) {
  const [filterCategory, setFilterCategory] = useState('ALL');
  const [minScore, setMinScore] = useState(0);
  const [searchQuery, setSearchQuery] = useState('');
  const [sortField, setSortField] = useState('convictionScore');
  const [sortAsc, setSortAsc] = useState(false);

  const stocks = useMemo(() => Object.values(marketData || {}), [marketData]);

  // Filtering
  const filteredStocks = useMemo(() => {
    return stocks.filter((stock) => {
      // Category filter
      if (filterCategory !== 'ALL' && stock.scoreCategory !== filterCategory) return false;
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
  }, [stocks, filterCategory, minScore, searchQuery]);

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
  const strongCount = stocks.filter(s => s.scoreCategory === 'STRONG' || s.scoreCategory === 'VERY_STRONG').length;
  const neutralCount = stocks.filter(s => s.scoreCategory === 'NEUTRAL').length;
  const weakCount = stocks.filter(s => s.scoreCategory === 'WEAK' || s.scoreCategory === 'VERY_WEAK').length;
  const avgScore = stocks.length > 0
    ? stocks.reduce((acc, curr) => acc + (curr.convictionScore ?? 50), 0) / stocks.length
    : 50.0;

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
            <p className="section-subtitle">Real-time equity ranking based on multi-factor scoring</p>
          </div>

          {/* Search & Min Score */}
          <div className="flex-row items-center gap-md flex-wrap">
            <div className="search-box">
              <Search size={14} color="var(--text-muted)" />
              <input
                type="text"
                placeholder="Filter ticker..."
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
                style={{ width: '120px' }}
              />
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
                style={{ width: '80px', accentColor: 'var(--accent-blue)', cursor: 'pointer' }}
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
                className={`filter-pill ${isSelected ? 'active' : ''}`}
                onClick={() => setFilterCategory(cat)}
              >
                {cat.replace('_', ' ')}
              </button>
            );
          })}
        </div>

        {/* Main Quantitative Table */}
        <div style={{ overflowX: 'auto' }}>
          <table className="data-table">
            <thead>
              <tr>
                <th style={{ width: '50px' }}>RANK</th>
                <th>SYMBOL</th>
                <th className="sortable" onClick={() => handleSort('price')}>
                  <div className="flex-row items-center gap-xs">
                    PRICE {sortField === 'price' && (sortAsc ? <ArrowUp size={12} /> : <ArrowDown size={12} />)}
                  </div>
                </th>
                <th className="sortable" onClick={() => handleSort('priceChangePercent')}>
                  <div className="flex-row items-center gap-xs">
                    CHANGE {sortField === 'priceChangePercent' && (sortAsc ? <ArrowUp size={12} /> : <ArrowDown size={12} />)}
                  </div>
                </th>
                <th className="sortable" onClick={() => handleSort('rsi')}>
                  <div className="flex-row items-center gap-xs">
                    RSI (14) {sortField === 'rsi' && (sortAsc ? <ArrowUp size={12} /> : <ArrowDown size={12} />)}
                  </div>
                </th>
                <th>MOMENTUM</th>
                <th>RVOL</th>
                <th className="sortable" onClick={() => handleSort('convictionScore')}>
                  <div className="flex-row items-center gap-xs">
                    CONVICTION {sortField === 'convictionScore' && (sortAsc ? <ArrowUp size={12} /> : <ArrowDown size={12} />)}
                  </div>
                </th>
                <th>SIGNAL RATING</th>
              </tr>
            </thead>
            <tbody>
              {sortedStocks.length === 0 ? (
                <tr>
                  <td colSpan={9} className="data-table-empty">
                    No equities match the active filter criteria.
                  </td>
                </tr>
              ) : (
                sortedStocks.map((stock, idx) => {
                  const isSelected = selectedSymbol === stock.symbol;
                  const isUp = (stock.priceChangePercent ?? 0) >= 0;
                  const cur = currencySymbol(stock, marketConfig);
                  const tag = exchangeTag(stock, marketConfig);
                  const score = stock.convictionScore != null ? Number(stock.convictionScore) : 50.0;
                  const category = stock.scoreCategory || 'NEUTRAL';
                  const scoreColor = getCategoryColor(category);

                  return (
                    <tr
                      key={stock.symbol}
                      className={isSelected ? 'selected' : ''}
                      onClick={() => onSelectSymbol(stock.symbol)}
                      style={{ cursor: 'pointer' }}
                    >
                      <td className="mono font-bold text-muted">#{idx + 1}</td>
                      <td>
                        <div className="flex-row items-center gap-xs">
                          <span className="mono font-extrabold text-primary">{stock.symbol}</span>
                          <span className="badge-exchange">{tag}</span>
                        </div>
                        <div className="text-secondary truncate" style={{ fontSize: '0.7rem', maxWidth: '140px' }}>
                          {stock.companyName}
                        </div>
                      </td>
                      <td className="mono font-bold">{cur}{stock.price != null ? Number(stock.price).toFixed(2) : '--'}</td>
                      <td className={`mono font-bold ${isUp ? 'text-bullish' : 'text-bearish'}`}>
                        {isUp ? '+' : ''}{stock.priceChangePercent != null ? stock.priceChangePercent.toFixed(2) : '0.00'}%
                      </td>
                      <td className="mono">
                        {stock.rsi != null && stock.rsi > 0 ? stock.rsi.toFixed(1) : '--'}
                      </td>
                      <td className={`mono ${stock.momentum >= 0 ? 'text-bullish' : 'text-bearish'}`}>
                        {stock.momentum != null ? `${stock.momentum > 0 ? '+' : ''}${stock.momentum.toFixed(2)}%` : '--'}
                      </td>
                      <td className="mono">
                        {stock.relativeVolume != null && stock.relativeVolume > 0 ? `${stock.relativeVolume.toFixed(2)}x` : '--'}
                      </td>
                      <td>
                        <div className="flex-row items-center gap-sm">
                          <span className="mono font-extrabold" style={{ fontSize: '1rem', color: scoreColor }}>
                            {score.toFixed(1)}
                          </span>
                          <div className="score-bar">
                            <div
                              className="score-bar-fill"
                              style={{ width: `${score}%`, backgroundColor: scoreColor }}
                            />
                          </div>
                        </div>
                      </td>
                      <td>
                        <span className="badge" style={{ color: scoreColor, background: `${scoreColor}18`, border: `1px solid ${scoreColor}30` }}>
                          {category.replace('_', ' ')}
                        </span>
                      </td>
                    </tr>
                  );
                })
              )}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
}
