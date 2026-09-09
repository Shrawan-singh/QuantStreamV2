'use client';

import React, { useState, useEffect } from 'react';
import { Bookmark, Plus, Trash2, ExternalLink } from 'lucide-react';
import { currencySymbol, isUsEquityStock } from '../lib/marketUtils';

export default function WatchlistManager({ apiBase, onSelectSymbol, currentMarketData, marketConfig }) {
  const [items, setItems] = useState([]);
  const [loading, setLoading] = useState(false);
  const [symbolInput, setSymbolInput] = useState('');
  const [notesInput, setNotesInput] = useState('');
  const [errorMsg, setErrorMsg] = useState(null);

  const fetchWatchlist = async () => {
    try {
      setLoading(true);
      const res = await fetch(`${apiBase || 'http://localhost:8080'}/api/watchlist`);
      if (res.ok) {
        const data = await res.json();
        setItems(data || []);
      }
    } catch (e) {
      console.debug('Failed to fetch watchlist', e);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchWatchlist();
  }, [apiBase]);

  const handleAdd = async (e) => {
    e.preventDefault();
    if (!symbolInput.trim()) return;

    try {
      setErrorMsg(null);
      const res = await fetch(`${apiBase || 'http://localhost:8080'}/api/watchlist`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          symbol: symbolInput.trim().toUpperCase(),
          notes: notesInput.trim()
        })
      });

      if (res.ok) {
        setSymbolInput('');
        setNotesInput('');
        fetchWatchlist();
      } else {
        const err = await res.json();
        setErrorMsg(err.error || 'Failed to add to watchlist');
      }
    } catch (err) {
      setErrorMsg('Network error connecting to backend');
    }
  };

  const handleRemove = async (sym) => {
    try {
      const res = await fetch(`${apiBase || 'http://localhost:8080'}/api/watchlist/${encodeURIComponent(sym)}`, {
        method: 'DELETE'
      });
      if (res.ok) {
        fetchWatchlist();
      }
    } catch (e) {
      console.debug('Failed to remove watchlist item', e);
    }
  };

  return (
    <div className="terminal-panel p-xl">
      {/* Title */}
      <div className="flex-row justify-between items-center mb-lg">
        <div>
          <h2 className="section-title flex-row items-center gap-xs">
            <Bookmark size={18} color="var(--accent-blue)" />
            PERSONAL WATCHLIST
          </h2>
          <p className="section-subtitle">Tracked custom instruments persisted in PostgreSQL with live analytics overlay</p>
        </div>
      </div>

      {/* Add Instrument Form */}
      <form onSubmit={handleAdd} className="flex-row items-center flex-wrap gap-md p-base mb-lg" style={{ background: 'var(--bg-secondary)', border: '1px solid var(--border-color)', borderRadius: '8px' }}>
        <input
          type="text"
          className="input"
          placeholder="Symbol (e.g. AAPL, RELIANCE)"
          value={symbolInput}
          onChange={(e) => setSymbolInput(e.target.value)}
          style={{ width: '180px' }}
        />

        <input
          type="text"
          className="input"
          placeholder="Personal research notes..."
          value={notesInput}
          onChange={(e) => setNotesInput(e.target.value)}
          style={{ flex: 1, minWidth: '200px' }}
        />

        <button type="submit" className="btn-primary">
          <Plus size={16} /> Add Symbol
        </button>

        {errorMsg && (
          <div className="w-full text-bearish" style={{ fontSize: '0.75rem', marginTop: '4px' }}>
            {errorMsg}
          </div>
        )}
      </form>

      {/* Watchlist Table */}
      <div style={{ overflowX: 'auto' }}>
        <table className="data-table">
          <thead>
            <tr>
              <th>SYMBOL</th>
              <th>LIVE PRICE</th>
              <th>24H CHG</th>
              <th>CONVICTION</th>
              <th>RESEARCH NOTES</th>
              <th style={{ width: '60px' }}>ACTION</th>
            </tr>
          </thead>
          <tbody>
            {items.length === 0 ? (
              <tr>
                <td colSpan={6} className="data-table-empty">
                  Your watchlist is empty. Add a symbol above to start tracking.
                </td>
              </tr>
            ) : (
              items.map((item) => {
                // Merge real-time socket data if available, fallback to item.analytics
                const liveData = (currentMarketData && currentMarketData[item.symbol]) || item.analytics;
                const cur = currencySymbol(liveData || { symbol: item.symbol }, marketConfig);
                const isUp = (liveData?.priceChangePercent ?? 0) >= 0;

                return (
                  <tr key={item.id || item.symbol}>
                    <td>
                      <div
                        onClick={() => onSelectSymbol(item.symbol)}
                        className="flex-row items-center gap-xs"
                        style={{ cursor: 'pointer' }}
                      >
                        <span className="mono font-extrabold text-accent">{item.symbol}</span>
                        <ExternalLink size={12} color="var(--text-muted)" />
                      </div>
                    </td>

                    <td className="mono font-bold text-primary">
                      {cur}{liveData?.price != null ? Number(liveData.price).toFixed(2) : '--'}
                    </td>

                    <td className={`mono font-bold ${isUp ? 'text-bullish' : 'text-bearish'}`}>
                      {isUp ? '+' : ''}{liveData?.priceChangePercent != null ? liveData.priceChangePercent.toFixed(2) : '0.00'}%
                    </td>

                    <td>
                      <span className="mono font-extrabold text-primary">
                        {liveData?.convictionScore != null ? Number(liveData.convictionScore).toFixed(1) : '--'}
                      </span>
                      <span className="text-muted" style={{ fontSize: '0.7rem', marginLeft: '6px' }}>
                        ({liveData?.scoreCategory?.replace('_', ' ') || 'Awaiting'})
                      </span>
                    </td>

                    <td className="text-secondary" style={{ maxWidth: '300px' }}>
                      <span className="truncate" style={{ display: 'block' }}>{item.notes || '—'}</span>
                    </td>

                    <td>
                      <button className="btn-danger" onClick={() => handleRemove(item.symbol)} title="Remove from watchlist">
                        <Trash2 size={15} />
                      </button>
                    </td>
                  </tr>
                );
              })
            )}
          </tbody>
        </table>
      </div>
    </div>
  );
}
