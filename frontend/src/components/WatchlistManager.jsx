'use client';

import React, { useState, useEffect, useRef, useMemo } from 'react';
import { Bookmark, Plus, Trash2, ExternalLink, Search, Check, AlertCircle } from 'lucide-react';
import { currencySymbol } from '../lib/marketUtils';

export default function WatchlistManager({ apiBase, onSelectSymbol, currentMarketData, marketConfig }) {
  const [items, setItems] = useState([]);
  const [supportedInstruments, setSupportedInstruments] = useState([]);
  const [loading, setLoading] = useState(false);
  const [symbolInput, setSymbolInput] = useState('');
  const [notesInput, setNotesInput] = useState('');
  const [errorMsg, setErrorMsg] = useState(null);
  const [showDropdown, setShowDropdown] = useState(false);
  const dropdownRef = useRef(null);

  const endpoint = apiBase || 'http://localhost:8080';

  // Fetch watchlist from PostgreSQL
  const fetchWatchlist = async () => {
    try {
      setLoading(true);
      const res = await fetch(`${endpoint}/api/watchlist`);
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

  // Fetch supported instruments universe for autocomplete
  useEffect(() => {
    async function loadInstruments() {
      try {
        const res = await fetch(`${endpoint}/api/instruments`);
        if (res.ok) {
          const list = await res.json();
          setSupportedInstruments(list || []);
        }
      } catch (e) {
        console.debug('Failed to load instrument registry', e);
      }
    }
    loadInstruments();
  }, [endpoint]);

  useEffect(() => {
    fetchWatchlist();
  }, [endpoint]);

  // Click outside listener for autocomplete dropdown
  useEffect(() => {
    const handleClickOutside = (e) => {
      if (dropdownRef.current && !dropdownRef.current.contains(e.target)) {
        setShowDropdown(false);
      }
    };
    document.addEventListener('mousedown', handleClickOutside);
    return () => document.removeEventListener('mousedown', handleClickOutside);
  }, []);

  // Filter instruments matching input query
  const filteredSuggestions = useMemo(() => {
    if (!symbolInput.trim()) return supportedInstruments.slice(0, 8);
    const q = symbolInput.trim().toUpperCase();
    return supportedInstruments.filter(
      (inst) =>
        inst.symbol.toUpperCase().includes(q) ||
        (inst.companyName && inst.companyName.toUpperCase().includes(q))
    ).slice(0, 8);
  }, [symbolInput, supportedInstruments]);

  const handleSelectSuggestion = (inst) => {
    setSymbolInput(inst.symbol);
    setShowDropdown(false);
    setErrorMsg(null);
  };

  const handleAdd = async (e) => {
    e.preventDefault();
    const normalized = symbolInput.trim().toUpperCase();

    if (!normalized) {
      setErrorMsg('Symbol is required');
      return;
    }

    // Client-side validation against supported market universe
    if (supportedInstruments.length > 0) {
      const isKnown = supportedInstruments.some((inst) => inst.symbol.toUpperCase() === normalized);
      if (!isKnown) {
        setErrorMsg('Instrument not found in the supported market universe.');
        return;
      }
    }

    // Prevent duplicate entries
    if (items.some((item) => item.symbol.toUpperCase() === normalized)) {
      setErrorMsg('Symbol already in watchlist');
      return;
    }

    try {
      setErrorMsg(null);
      const res = await fetch(`${endpoint}/api/watchlist`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          symbol: normalized,
          notes: notesInput.trim()
        })
      });

      if (res.ok) {
        setSymbolInput('');
        setNotesInput('');
        setShowDropdown(false);
        fetchWatchlist();
      } else {
        const err = await res.json();
        setErrorMsg(err.error || 'Instrument not found in the supported market universe.');
      }
    } catch (err) {
      setErrorMsg('Network error connecting to backend');
    }
  };

  const handleRemove = async (sym) => {
    const targetSym = sym.trim().toUpperCase();
    const previousItems = [...items];

    // Optimistic UI deletion: removed symbol disappears immediately
    setItems((prev) => prev.filter((i) => i.symbol.toUpperCase() !== targetSym));

    try {
      const res = await fetch(`${endpoint}/api/watchlist/${encodeURIComponent(targetSym)}`, {
        method: 'DELETE'
      });
      if (!res.ok) {
        // Rollback on failure
        setItems(previousItems);
        setErrorMsg('Failed to remove symbol from database');
      }
    } catch (e) {
      setItems(previousItems);
      setErrorMsg('Network error deleting watchlist item');
    }
  };

  return (
    <div className="terminal-panel p-xl">
      {/* Title Header */}
      <div className="flex-row justify-between items-center mb-lg">
        <div>
          <h2 className="section-title flex-row items-center gap-xs">
            <Bookmark size={18} color="var(--accent-blue)" />
            PERSONAL WATCHLIST
          </h2>
          <p className="section-subtitle">
            Curated custom instruments persisted in PostgreSQL with live analytics overlay
          </p>
        </div>
        <div className="text-secondary mono" style={{ fontSize: '0.75rem' }}>
          {items.length} {items.length === 1 ? 'INSTRUMENT' : 'INSTRUMENTS'} TRACKED
        </div>
      </div>

      {/* Add Instrument Form with Autocomplete */}
      <form
        onSubmit={handleAdd}
        className="flex-row items-center flex-wrap gap-md p-base mb-lg"
        style={{
          background: 'var(--bg-secondary)',
          border: '1px solid var(--border-color)',
          borderRadius: '8px',
          position: 'relative'
        }}
      >
        <div ref={dropdownRef} style={{ position: 'relative', width: '220px' }}>
          <label className="label-caps mb-xs" style={{ display: 'block' }}>SELECT INSTRUMENT</label>
          <div style={{ position: 'relative' }}>
            <input
              type="text"
              className="input w-full"
              placeholder="Type symbol or name..."
              value={symbolInput}
              onChange={(e) => {
                setSymbolInput(e.target.value);
                setShowDropdown(true);
                setErrorMsg(null);
              }}
              onFocus={() => setShowDropdown(true)}
              style={{ textTransform: 'uppercase' }}
            />
            <Search
              size={14}
              color="var(--text-muted)"
              style={{ position: 'absolute', right: '10px', top: '50%', transform: 'translateY(-50%)', pointerEvents: 'none' }}
            />
          </div>

          {/* Autocomplete Dropdown */}
          {showDropdown && filteredSuggestions.length > 0 && (
            <div
              className="search-dropdown"
              style={{
                position: 'absolute',
                top: '100%',
                left: 0,
                right: 0,
                zIndex: 50,
                marginTop: '4px',
                maxHeight: '260px',
                overflowY: 'auto'
              }}
            >
              {filteredSuggestions.map((inst) => (
                <div
                  key={inst.symbol}
                  className="search-dropdown-item"
                  onClick={() => handleSelectSuggestion(inst)}
                  style={{ cursor: 'pointer' }}
                >
                  <div>
                    <span className="mono font-extrabold text-accent" style={{ fontSize: '0.82rem' }}>
                      {inst.symbol}
                    </span>
                    <span className="text-secondary" style={{ fontSize: '0.72rem', display: 'block' }}>
                      {inst.companyName}
                    </span>
                  </div>
                  <span className="badge badge-exchange" style={{ fontSize: '0.65rem' }}>
                    {inst.exchange}
                  </span>
                </div>
              ))}
            </div>
          )}
        </div>

        <div style={{ flex: 1, minWidth: '220px' }}>
          <label className="label-caps mb-xs" style={{ display: 'block' }}>RESEARCH NOTES</label>
          <input
            type="text"
            className="input w-full"
            placeholder="Personal thesis or catalyst notes..."
            value={notesInput}
            onChange={(e) => setNotesInput(e.target.value)}
          />
        </div>

        <div style={{ alignSelf: 'flex-end', marginTop: '18px' }}>
          <button type="submit" className="btn-primary">
            <Plus size={16} /> Add to Watchlist
          </button>
        </div>

        {errorMsg && (
          <div className="w-full flex-row items-center gap-xs text-bearish font-bold mt-xs" style={{ fontSize: '0.78rem' }}>
            <AlertCircle size={14} />
            <span>{errorMsg}</span>
          </div>
        )}
      </form>

      {/* Watchlist Table */}
      <div style={{ overflowX: 'auto' }}>
        <table className="data-table">
          <thead>
            <tr>
              <th>SYMBOL</th>
              <th>COMPANY</th>
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
                <td colSpan={7} className="data-table-empty">
                  Your watchlist is empty. Select an instrument above to begin tracking.
                </td>
              </tr>
            ) : (
              items.map((item) => {
                const sym = item.symbol.toUpperCase();
                const liveData = (currentMarketData && currentMarketData[sym]) || item.analytics;
                const cur = currencySymbol(liveData || { symbol: sym }, marketConfig);

                // Consistency guarantees:
                // Only show price if live price is present and numeric; otherwise "-- (Awaiting data)"
                const hasValidPrice = liveData?.price != null && !isNaN(Number(liveData.price)) && Number(liveData.price) > 0;
                const isUp = (liveData?.priceChangePercent ?? 0) >= 0;

                // Only show conviction if analytics are warmed up and present; otherwise "-- (Awaiting data)"
                const hasConviction = liveData?.ready !== false && liveData?.convictionScore != null && !isNaN(Number(liveData.convictionScore));

                return (
                  <tr key={item.id || sym}>
                    <td>
                      <div
                        onClick={() => onSelectSymbol(sym)}
                        className="flex-row items-center gap-xs"
                        style={{ cursor: 'pointer' }}
                        title="View stock details"
                      >
                        <span className="mono font-extrabold text-accent">{sym}</span>
                        <ExternalLink size={12} color="var(--text-muted)" />
                      </div>
                    </td>

                    <td className="text-secondary" style={{ fontSize: '0.78rem' }}>
                      {item.companyName || liveData?.companyName || sym}
                    </td>

                    <td className="mono font-bold text-primary">
                      {hasValidPrice ? (
                        `${cur}${Number(liveData.price).toFixed(2)}`
                      ) : (
                        <span className="text-muted" style={{ fontSize: '0.75rem' }}>-- (Awaiting data)</span>
                      )}
                    </td>

                    <td className={`mono font-bold ${hasValidPrice ? (isUp ? 'text-bullish' : 'text-bearish') : 'text-muted'}`}>
                      {hasValidPrice ? (
                        `${isUp ? '+' : ''}${liveData.priceChangePercent != null ? liveData.priceChangePercent.toFixed(2) : '0.00'}%`
                      ) : (
                        '--'
                      )}
                    </td>

                    <td>
                      {hasConviction ? (
                        <div className="flex-row items-center gap-xs">
                          <span className="mono font-extrabold text-primary">
                            {Number(liveData.convictionScore).toFixed(1)}
                          </span>
                          <span className="text-muted" style={{ fontSize: '0.7rem' }}>
                            ({liveData.scoreCategory ? liveData.scoreCategory.replace('_', ' ') : 'NEUTRAL'})
                          </span>
                        </div>
                      ) : (
                        <span className="text-muted" style={{ fontSize: '0.75rem' }}>-- (Awaiting data)</span>
                      )}
                    </td>

                    <td className="text-secondary" style={{ maxWidth: '280px' }}>
                      <span className="truncate" style={{ display: 'block' }}>{item.notes || '—'}</span>
                    </td>

                    <td>
                      <button
                        className="btn-danger"
                        onClick={() => handleRemove(sym)}
                        title="Remove from watchlist"
                      >
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
