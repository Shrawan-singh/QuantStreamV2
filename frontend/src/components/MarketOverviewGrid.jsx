'use client';

/**
 * ==============================================================================
 * Market Overview Grid (frontend/src/components/MarketOverviewGrid.jsx)
 * ==============================================================================
 *
 * WHAT IS THIS COMPONENT FOR? (Plain English):
 * When you first visit the QuantStream dashboard, this component displays a neat,
 * responsive grid of interactive cards representing the top market leaders
 * (or stocks from your personal watchlist).
 *
 * KEY FEATURES ON EACH CARD:
 * 1. LIVE FLASH ANIMATIONS:
 *    When a new price tick arrives from the server, the entire card subtly flashes
 *    emerald green if the price rose, or crimson red if the price dropped!
 * 2. SYMBOL & BADGES:
 *    Shows the ticker symbol (e.g., 'AAPL'), exchange tag ('US' or 'NSE'),
 *    and the full company name (e.g., 'Apple Inc.').
 * 3. REAL-TIME PRICE & CHANGE:
 *    Current trading price and today's percentage change (+2.4% with an up arrow).
 * 4. CONVICTION SCORE BAR:
 *    A miniature horizontal progress bar colored by conviction tier
 *    (Green for Very Strong, Cyan for Strong, Orange for Weak, Red for Very Weak).
 * 5. CLICK INTERACTIONS:
 *    - Single Click: Selects this stock as the active "Spotlight" stock on the dashboard.
 *    - Double Click (or clicking the small arrow icon): Jumps straight into the full
 *      Stock Detail view.
 * ==============================================================================
 */

import React from 'react';
import { TrendingUp, TrendingDown, ArrowRight } from 'lucide-react';
import { currencySymbol, exchangeTag } from '../lib/marketUtils';

export default function MarketOverviewGrid({ stocks, selectedSymbol, onSelectSymbol, onDrillDown }) {
  // If the server is still starting up or loading data, display skeleton placeholder boxes
  if (stocks.length === 0) {
    return (
      <div className="terminal-panel p-xl text-center text-muted">
        <p>Connecting to market data stream...</p>
        <div className="flex-row items-center justify-center gap-sm mt-lg">
          <div className="skeleton" style={{ width: '200px', height: '100px' }} />
          <div className="skeleton" style={{ width: '200px', height: '100px' }} />
          <div className="skeleton" style={{ width: '200px', height: '100px' }} />
        </div>
      </div>
    );
  }


  return (
    <div className="grid-auto grid-auto-fill-sm">
      {stocks.map((stock) => {
        const isSelected = selectedSymbol === stock.symbol;
        const isUp = (stock.priceChangePercent ?? 0) >= 0;
        const scoreColor = getScoreColor(stock.scoreCategory);
        const flashClass = stock.tickDirection === 'up' ? 'flash-up' : stock.tickDirection === 'down' ? 'flash-down' : '';
        const cur = currencySymbol(stock);
        const tag = exchangeTag(stock);

        return (
          <div
            key={stock.symbol}
            data-tick-dir={stock.tickDirection || 'none'}
            className={`instrument-card ${isSelected ? 'selected' : ''} ${flashClass}`}
            onClick={() => onSelectSymbol(stock.symbol)}
            onDoubleClick={() => onDrillDown && onDrillDown(stock.symbol)}
          >
            {/* Top Row: Symbol + Exchange */}
            <div className="flex-row items-center justify-between mb-sm">
              <div className="flex-row items-center gap-sm">
                <span className="mono font-extrabold" style={{ fontSize: '0.95rem' }}>
                  {stock.symbol}
                </span>
                <span className="badge-exchange">{tag}</span>
              </div>
              {onDrillDown && (
                <button
                  className="btn-ghost"
                  onClick={(e) => { e.stopPropagation(); onDrillDown(stock.symbol); }}
                  title="Full analysis"
                  style={{ padding: '2px' }}
                >
                  <ArrowRight size={14} color="var(--text-muted)" />
                </button>
              )}
            </div>

            {/* Company Name */}
            <div className="truncate text-secondary mb-sm" style={{ fontSize: '0.72rem' }}>
              {stock.companyName}
            </div>

            {/* Price + Change */}
            <div className="flex-row items-baseline justify-between">
              <span className="mono font-extrabold" style={{ fontSize: '1.25rem' }}>
                {cur}{stock.price != null ? Number(stock.price).toFixed(2) : '--'}
              </span>
              <span
                className="mono font-bold flex-row items-center gap-2xs"
                style={{ fontSize: '0.82rem', color: isUp ? 'var(--bullish)' : 'var(--bearish)' }}
              >
                {isUp ? <TrendingUp size={13} /> : <TrendingDown size={13} />}
                {isUp ? '+' : ''}
                {stock.priceChangePercent != null ? stock.priceChangePercent.toFixed(2) : '0.00'}%
              </span>
            </div>

            {/* Bottom Row: Conviction Score */}
            <hr className="separator" />
            <div className="flex-row items-center justify-between">
              <span className="label-caps">CONVICTION</span>
              <div className="flex-row items-center gap-sm">
                <span
                  className="mono font-extrabold"
                  style={{ fontSize: '0.88rem', color: scoreColor }}
                >
                  {stock.convictionScore != null ? Number(stock.convictionScore).toFixed(1) : '--'}
                </span>
                <div className="score-bar">
                  <div
                    className="score-bar-fill"
                    style={{
                      width: `${Math.min(100, Math.max(0, stock.convictionScore ?? 50))}%`,
                      backgroundColor: scoreColor,
                    }}
                  />
                </div>
              </div>
            </div>
          </div>
        );
      })}
    </div>
  );
}

function getScoreColor(category) {
  switch (category) {
    case 'VERY_STRONG': return 'var(--score-very-strong)';
    case 'STRONG':      return 'var(--score-strong)';
    case 'NEUTRAL':     return 'var(--score-neutral)';
    case 'WEAK':        return 'var(--score-weak)';
    case 'VERY_WEAK':   return 'var(--score-very-weak)';
    default:            return 'var(--score-neutral)';
  }
}
