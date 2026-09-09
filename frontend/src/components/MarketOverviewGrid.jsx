'use client';

import React, { useState, useRef, useEffect } from 'react';
import { TrendingUp, TrendingDown, ArrowRight } from 'lucide-react';
import { currencySymbol, exchangeTag } from '../lib/marketUtils';

export default function MarketOverviewGrid({ stocks, selectedSymbol, onSelectSymbol, onDrillDown }) {
  const [flashMap, setFlashMap] = useState({});
  const prevPrices = useRef({});

  // Track tick flashes
  useEffect(() => {
    stocks.forEach((stock) => {
      const prevPrice = prevPrices.current[stock.symbol];
      if (prevPrice !== undefined && stock.price !== undefined) {
        const newPrice = Number(stock.price);
        if (newPrice > Number(prevPrice)) {
          setFlashMap((prev) => ({ ...prev, [stock.symbol]: 'flash-up' }));
          setTimeout(() => setFlashMap((prev) => ({ ...prev, [stock.symbol]: '' })), 700);
        } else if (newPrice < Number(prevPrice)) {
          setFlashMap((prev) => ({ ...prev, [stock.symbol]: 'flash-down' }));
          setTimeout(() => setFlashMap((prev) => ({ ...prev, [stock.symbol]: '' })), 700);
        }
      }
      prevPrices.current[stock.symbol] = stock.price;
    });
  }, [stocks]);

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
        const flashClass = flashMap[stock.symbol] || '';
        const cur = currencySymbol(stock);
        const tag = exchangeTag(stock);

        return (
          <div
            key={stock.symbol}
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
