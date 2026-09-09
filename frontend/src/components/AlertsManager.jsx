'use client';

import React, { useState, useEffect, useRef, useMemo } from 'react';
import { Bell, Plus, Trash2, Power, AlertTriangle, RotateCcw, History, Search, CheckCircle2, AlertCircle } from 'lucide-react';

export default function AlertsManager({ apiBase, latestAlertEvent }) {
  const [alerts, setAlerts] = useState([]);
  const [historyItems, setHistoryItems] = useState([]);
  const [supportedInstruments, setSupportedInstruments] = useState([]);
  const [loading, setLoading] = useState(false);
  const [symbol, setSymbol] = useState('');
  const [conditionType, setConditionType] = useState('PRICE_ABOVE');
  const [threshold, setThreshold] = useState('');
  const [statusMsg, setStatusMsg] = useState(null);
  const [errorMsg, setErrorMsg] = useState(null);
  const [showHistoryModal, setShowHistoryModal] = useState(false);
  const [selectedHistoryAlert, setSelectedHistoryAlert] = useState(null);
  const [showDropdown, setShowDropdown] = useState(false);
  const dropdownRef = useRef(null);

  const endpoint = apiBase || 'http://localhost:8080';

  const fetchAlerts = async () => {
    try {
      setLoading(true);
      const res = await fetch(`${endpoint}/api/alerts`);
      if (res.ok) {
        const data = await res.json();
        setAlerts(data || []);
      }
    } catch (e) {
      console.debug('Failed to fetch alerts', e);
    } finally {
      setLoading(false);
    }
  };

  const fetchHistory = async () => {
    try {
      const res = await fetch(`${endpoint}/api/alerts/history`);
      if (res.ok) {
        const data = await res.json();
        setHistoryItems(data || []);
      }
    } catch (e) {
      console.debug('Failed to fetch alert history', e);
    }
  };

  // Load supported instruments for universe validation and autocomplete
  useEffect(() => {
    async function loadInstruments() {
      try {
        const res = await fetch(`${endpoint}/api/instruments`);
        if (res.ok) {
          const list = await res.json();
          setSupportedInstruments(list || []);
          if (list.length > 0 && !symbol) {
            setSymbol(list[0].symbol);
          }
        }
      } catch (e) {
        console.debug('Failed to load instrument registry for alerts', e);
      }
    }
    loadInstruments();
  }, [endpoint]);

  useEffect(() => {
    fetchAlerts();
    fetchHistory();
  }, [endpoint]);

  // Real-time WebSocket trigger listener
  useEffect(() => {
    if (!latestAlertEvent) return;

    // Immediately update local alerts state with the trigger
    setAlerts((prev) =>
      prev.map((a) => {
        if (a.id === latestAlertEvent.alertId || (a.symbol === latestAlertEvent.symbol && a.conditionType === latestAlertEvent.conditionType)) {
          return {
            ...a,
            triggered: true,
            triggeredValue: latestAlertEvent.triggeredValue,
            triggeredAt: latestAlertEvent.triggeredAt,
            enabled: false
          };
        }
        return a;
      })
    );

    // Append to local history
    setHistoryItems((prev) => [
      {
        id: Date.now(),
        alertId: latestAlertEvent.alertId,
        symbol: latestAlertEvent.symbol,
        conditionType: latestAlertEvent.conditionType,
        threshold: latestAlertEvent.threshold,
        triggeredValue: latestAlertEvent.triggeredValue,
        triggeredAt: latestAlertEvent.triggeredAt
      },
      ...prev
    ]);

    setStatusMsg(`TRIGGER FIRED: ${latestAlertEvent.symbol} reached ${latestAlertEvent.triggeredValue} (${latestAlertEvent.conditionType})`);
  }, [latestAlertEvent]);

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

  const filteredSuggestions = useMemo(() => {
    if (!symbol.trim()) return supportedInstruments.slice(0, 8);
    const q = symbol.trim().toUpperCase();
    return supportedInstruments.filter(
      (inst) =>
        inst.symbol.toUpperCase().includes(q) ||
        (inst.companyName && inst.companyName.toUpperCase().includes(q))
    ).slice(0, 8);
  }, [symbol, supportedInstruments]);

  const handleSelectSuggestion = (inst) => {
    setSymbol(inst.symbol);
    setShowDropdown(false);
    setErrorMsg(null);
  };

  const handleCreateAlert = async (e) => {
    e.preventDefault();
    setErrorMsg(null);
    setStatusMsg(null);

    const normalizedSym = symbol.trim().toUpperCase();
    if (!normalizedSym) {
      setErrorMsg('Symbol is required');
      return;
    }

    // Validate against registry
    if (supportedInstruments.length > 0) {
      const isKnown = supportedInstruments.some((i) => i.symbol.toUpperCase() === normalizedSym);
      if (!isKnown) {
        setErrorMsg('Instrument not found in the supported market universe.');
        return;
      }
    }

    if (!threshold || isNaN(Number(threshold)) || Number(threshold) <= 0) {
      setErrorMsg('Threshold must be a valid positive number');
      return;
    }

    if (conditionType.startsWith('SCORE_')) {
      const numThresh = Number(threshold);
      if (numThresh < 0 || numThresh > 100) {
        setErrorMsg('Score threshold must be between 0.0 and 100.0');
        return;
      }
    }

    try {
      const res = await fetch(`${endpoint}/api/alerts`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          symbol: normalizedSym,
          conditionType,
          threshold: Number(threshold),
          enabled: true
        })
      });

      if (res.ok) {
        setStatusMsg('Alert condition registered successfully');
        setThreshold('');
        fetchAlerts();
        setTimeout(() => setStatusMsg(null), 4000);
      } else {
        const err = await res.json();
        setErrorMsg(err.error || 'Failed to create alert');
      }
    } catch (err) {
      setErrorMsg('Network error connecting to backend');
    }
  };

  const handleToggle = async (id) => {
    try {
      await fetch(`${endpoint}/api/alerts/${id}/toggle`, {
        method: 'PUT'
      });
      fetchAlerts();
    } catch (err) {
      console.error('Failed to toggle alert', err);
    }
  };

  const handleReset = async (id) => {
    try {
      const res = await fetch(`${endpoint}/api/alerts/${id}/reset`, {
        method: 'PUT'
      });
      if (res.ok) {
        setStatusMsg('Alert re-armed to ACTIVE state');
        fetchAlerts();
        setTimeout(() => setStatusMsg(null), 3000);
      }
    } catch (err) {
      console.error('Failed to reset alert', err);
    }
  };

  const handleDelete = async (id) => {
    // Optimistic UI update
    setAlerts((prev) => prev.filter((a) => a.id !== id));
    try {
      await fetch(`${endpoint}/api/alerts/${id}`, {
        method: 'DELETE'
      });
      fetchAlerts();
    } catch (err) {
      console.error('Failed to delete alert', err);
    }
  };

  return (
    <div className="terminal-panel p-xl">
      {/* Title Header */}
      <div className="flex-row justify-between items-center mb-lg">
        <div>
          <h2 className="section-title flex-row items-center gap-xs">
            <Bell size={18} color="var(--neutral)" />
            AUTONOMOUS ALERT TRIGGERS
          </h2>
          <p className="section-subtitle">
            One-shot threshold execution evaluated on every tick by the stream worker pool
          </p>
        </div>
        <button
          className="btn-secondary flex-row items-center gap-xs"
          onClick={() => {
            setSelectedHistoryAlert(null);
            setShowHistoryModal(true);
          }}
          style={{ fontSize: '0.78rem' }}
        >
          <History size={14} /> View Trigger History ({historyItems.length})
        </button>
      </div>

      {/* Creation Form with Registry Autocomplete */}
      <form
        onSubmit={handleCreateAlert}
        className="flex-row items-center flex-wrap gap-md p-base mb-xl"
        style={{
          background: 'var(--bg-secondary)',
          border: '1px solid var(--border-color)',
          borderRadius: '8px'
        }}
      >
        <div ref={dropdownRef} style={{ position: 'relative', width: '180px' }}>
          <label className="label-caps mb-xs" style={{ display: 'block' }}>INSTRUMENT</label>
          <div style={{ position: 'relative' }}>
            <input
              type="text"
              className="input w-full"
              placeholder="Search..."
              value={symbol}
              onChange={(e) => {
                setSymbol(e.target.value);
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
                maxHeight: '220px',
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
                    <span className="text-secondary" style={{ fontSize: '0.7rem', display: 'block' }}>
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

        <div>
          <label className="label-caps mb-xs" style={{ display: 'block' }}>TRIGGER CONDITION</label>
          <select
            className="select"
            value={conditionType}
            onChange={(e) => setConditionType(e.target.value)}
          >
            <option value="PRICE_ABOVE">Price Above (&gt;)</option>
            <option value="PRICE_BELOW">Price Below (&lt;)</option>
            <option value="SCORE_ABOVE">Conviction Score Above (&gt;)</option>
            <option value="SCORE_BELOW">Conviction Score Below (&lt;)</option>
          </select>
        </div>

        <div>
          <label className="label-caps mb-xs" style={{ display: 'block' }}>NUMERIC THRESHOLD</label>
          <input
            type="number"
            step="any"
            className="input"
            placeholder={conditionType.startsWith('SCORE') ? '0 - 100' : 'Price value'}
            value={threshold}
            onChange={(e) => setThreshold(e.target.value)}
            style={{ width: '150px' }}
          />
        </div>

        <div style={{ alignSelf: 'flex-end', marginTop: '18px' }}>
          <button type="submit" className="btn-primary">
            <Plus size={16} /> Register Alert
          </button>
        </div>

        {statusMsg && (
          <div className="w-full flex-row items-center gap-xs text-bullish font-bold mt-xs" style={{ fontSize: '0.78rem' }}>
            <CheckCircle2 size={14} />
            <span>{statusMsg}</span>
          </div>
        )}

        {errorMsg && (
          <div className="w-full flex-row items-center gap-xs text-bearish font-bold mt-xs" style={{ fontSize: '0.78rem' }}>
            <AlertCircle size={14} />
            <span>{errorMsg}</span>
          </div>
        )}
      </form>

      {/* Active Alerts Table */}
      <div style={{ overflowX: 'auto' }}>
        <table className="data-table">
          <thead>
            <tr>
              <th>SYMBOL</th>
              <th>CONDITION</th>
              <th>THRESHOLD</th>
              <th>STATE</th>
              <th>TRIGGER DETAILS</th>
              <th style={{ width: '120px' }}>ACTIONS</th>
            </tr>
          </thead>
          <tbody>
            {alerts.length === 0 ? (
              <tr>
                <td colSpan={6} className="data-table-empty">
                  No active alert triggers registered. Use the form above to add an autonomous alert.
                </td>
              </tr>
            ) : (
              alerts.map((alert) => {
                const isTriggered = alert.triggered === true;
                const isEnabled = alert.enabled === true;
                const state = isTriggered ? 'TRIGGERED' : (isEnabled ? 'ACTIVE' : 'DISABLED');

                return (
                  <tr key={alert.id} style={{ background: isTriggered ? 'rgba(245, 158, 11, 0.04)' : undefined }}>
                    <td className="mono font-extrabold text-primary">{alert.symbol}</td>
                    <td className="text-secondary">{alert.conditionType?.replace('_', ' ')}</td>
                    <td className="mono font-bold text-accent">
                      {alert.conditionType?.startsWith('SCORE') ? alert.threshold : Number(alert.threshold).toFixed(2)}
                    </td>

                    <td>
                      {state === 'ACTIVE' && (
                        <span className="badge badge-bullish flex-row items-center gap-xs">
                          <span className="live-dot" style={{ width: '6px', height: '6px' }} />
                          ACTIVE
                        </span>
                      )}
                      {state === 'TRIGGERED' && (
                        <span className="badge badge-neutral flex-row items-center gap-xs font-bold">
                          <AlertTriangle size={12} />
                          TRIGGERED
                        </span>
                      )}
                      {state === 'DISABLED' && (
                        <span className="badge badge-muted">
                          DISABLED
                        </span>
                      )}
                    </td>

                    <td>
                      {isTriggered ? (
                        <div>
                          <div className="mono font-bold text-neutral" style={{ fontSize: '0.8rem' }}>
                            Breached at: {Number(alert.triggeredValue).toFixed(2)}
                          </div>
                          <div className="text-muted" style={{ fontSize: '0.7rem' }}>
                            {alert.triggeredAt ? new Date(alert.triggeredAt).toLocaleTimeString() : 'Recent'}
                          </div>
                        </div>
                      ) : isEnabled ? (
                        <span className="text-secondary" style={{ fontSize: '0.75rem' }}>
                          Standing by (Monitoring incoming ticks)
                        </span>
                      ) : (
                        <span className="text-muted" style={{ fontSize: '0.75rem' }}>
                          Alert is inactive
                        </span>
                      )}
                    </td>

                    <td>
                      <div className="flex-row items-center gap-sm">
                        {isTriggered ? (
                          <button
                            className="btn-secondary"
                            onClick={() => handleReset(alert.id)}
                            title="Re-arm alert to ACTIVE state"
                            style={{ padding: '4px 8px', fontSize: '0.72rem' }}
                          >
                            <RotateCcw size={12} /> Re-arm
                          </button>
                        ) : (
                          <button
                            className="btn-ghost"
                            onClick={() => handleToggle(alert.id)}
                            style={{ color: isEnabled ? 'var(--bullish)' : 'var(--text-muted)' }}
                            title={isEnabled ? "Disable alert" : "Enable alert"}
                          >
                            <Power size={14} />
                          </button>
                        )}

                        <button
                          className="btn-danger"
                          onClick={() => handleDelete(alert.id)}
                          title="Delete alert"
                        >
                          <Trash2 size={14} />
                        </button>
                      </div>
                    </td>
                  </tr>
                );
              })
            )}
          </tbody>
        </table>
      </div>

      {/* Trigger History Modal */}
      {showHistoryModal && (
        <div className="modal-overlay">
          <div className="modal-content" style={{ maxWidth: '650px', width: '90%' }}>
            <div className="flex-row justify-between items-center mb-base">
              <div className="flex-row items-center gap-sm">
                <History size={18} color="var(--neutral)" />
                <h3 className="section-title">Trigger History Log</h3>
              </div>
              <button className="btn-ghost" onClick={() => setShowHistoryModal(false)}>
                ✕
              </button>
            </div>

            <p className="section-subtitle mb-base">
              Deterministic one-shot event log recorded in PostgreSQL upon each threshold condition crossing.
            </p>

            <div style={{ maxHeight: '350px', overflowY: 'auto' }}>
              {historyItems.length === 0 ? (
                <div className="text-muted p-xl text-center">No alert trigger events recorded yet.</div>
              ) : (
                <table className="data-table">
                  <thead>
                    <tr>
                      <th>TIMESTAMP</th>
                      <th>SYMBOL</th>
                      <th>CONDITION</th>
                      <th>THRESHOLD</th>
                      <th>BREACH VALUE</th>
                    </tr>
                  </thead>
                  <tbody>
                    {historyItems.map((h, idx) => (
                      <tr key={h.id || idx}>
                        <td className="mono text-muted" style={{ fontSize: '0.72rem' }}>
                          {h.triggeredAt ? new Date(h.triggeredAt).toLocaleString() : '—'}
                        </td>
                        <td className="mono font-bold text-accent">{h.symbol}</td>
                        <td className="text-secondary">{h.conditionType?.replace('_', ' ')}</td>
                        <td className="mono text-secondary">{Number(h.threshold).toFixed(2)}</td>
                        <td className="mono font-bold text-bullish">{Number(h.triggeredValue).toFixed(2)}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              )}
            </div>

            <div className="flex-row justify-end mt-base">
              <button className="btn-secondary" onClick={() => setShowHistoryModal(false)}>
                Close
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
