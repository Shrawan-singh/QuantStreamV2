'use client';

import React, { useState, useEffect } from 'react';
import { Bell, Plus, Trash2, Power, AlertTriangle } from 'lucide-react';

export default function AlertsManager({ apiBase }) {
  const [alerts, setAlerts] = useState([]);
  const [loading, setLoading] = useState(false);
  const [symbol, setSymbol] = useState('AAPL');
  const [conditionType, setConditionType] = useState('PRICE_ABOVE');
  const [threshold, setThreshold] = useState('200');
  const [statusMsg, setStatusMsg] = useState(null);

  const fetchAlerts = async () => {
    try {
      setLoading(true);
      const res = await fetch(`${apiBase || 'http://localhost:8080'}/api/alerts`);
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

  useEffect(() => {
    fetchAlerts();
  }, [apiBase]);

  const handleCreateAlert = async (e) => {
    e.preventDefault();
    if (!threshold || isNaN(Number(threshold))) return;

    try {
      const res = await fetch(`${apiBase || 'http://localhost:8080'}/api/alerts`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          symbol: symbol.toUpperCase().trim(),
          conditionType,
          threshold: Number(threshold),
          enabled: true
        })
      });

      if (res.ok) {
        setStatusMsg('Alert condition registered successfully');
        setTimeout(() => setStatusMsg(null), 3000);
        fetchAlerts();
      }
    } catch (err) {
      console.error('Failed to create alert', err);
    }
  };

  const handleToggle = async (id) => {
    try {
      await fetch(`${apiBase || 'http://localhost:8080'}/api/alerts/${id}/toggle`, {
        method: 'PUT'
      });
      fetchAlerts();
    } catch (err) {
      console.error('Failed to toggle alert', err);
    }
  };

  const handleDelete = async (id) => {
    try {
      await fetch(`${apiBase || 'http://localhost:8080'}/api/alerts/${id}`, {
        method: 'DELETE'
      });
      fetchAlerts();
    } catch (err) {
      console.error('Failed to delete alert', err);
    }
  };

  return (
    <div className="terminal-panel p-xl">
      {/* Title */}
      <div className="flex-row justify-between items-center mb-lg">
        <div>
          <h2 className="section-title flex-row items-center gap-xs">
            <Bell size={18} color="var(--neutral)" />
            AUTONOMOUS ALERT TRIGGERS
          </h2>
          <p className="section-subtitle">Real-time threshold monitoring evaluated by the stream worker pool on every incoming tick</p>
        </div>
      </div>

      {/* Creation Form */}
      <form onSubmit={handleCreateAlert} className="flex-row items-center flex-wrap gap-md p-base mb-xl" style={{ background: 'var(--bg-secondary)', border: '1px solid var(--border-color)', borderRadius: '8px' }}>
        <div>
          <label className="label-caps mb-xs" style={{ display: 'block' }}>INSTRUMENT</label>
          <input
            type="text"
            className="input"
            value={symbol}
            onChange={(e) => setSymbol(e.target.value)}
            style={{ width: '120px' }}
          />
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
            <option value="RSI_ABOVE">RSI Above (&gt;)</option>
            <option value="RSI_BELOW">RSI Below (&lt;)</option>
          </select>
        </div>

        <div>
          <label className="label-caps mb-xs" style={{ display: 'block' }}>NUMERIC THRESHOLD</label>
          <input
            type="number"
            step="any"
            className="input"
            value={threshold}
            onChange={(e) => setThreshold(e.target.value)}
            style={{ width: '140px' }}
          />
        </div>

        <div style={{ alignSelf: 'flex-end', marginTop: '18px' }}>
          <button type="submit" className="btn-primary">
            <Plus size={16} /> Register Alert
          </button>
        </div>

        {statusMsg && (
          <div className="w-full text-bullish font-bold mt-xs" style={{ fontSize: '0.75rem' }}>
            {statusMsg}
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
              <th>TRIGGER HISTORY</th>
              <th style={{ width: '100px' }}>ACTIONS</th>
            </tr>
          </thead>
          <tbody>
            {alerts.length === 0 ? (
              <tr>
                <td colSpan={6} className="data-table-empty">
                  No active alert triggers registered. Use the form above to add one.
                </td>
              </tr>
            ) : (
              alerts.map((alert) => (
                <tr key={alert.id}>
                  <td className="mono font-extrabold text-primary">{alert.symbol}</td>
                  <td className="text-secondary">{alert.conditionType?.replace('_', ' ')}</td>
                  <td className="mono font-bold text-accent">{Number(alert.threshold).toFixed(2)}</td>
                  <td>
                    <span className={`badge ${alert.enabled ? 'badge-bullish' : 'badge-muted'}`}>
                      {alert.enabled ? 'ACTIVE' : 'DISABLED'}
                    </span>
                  </td>
                  <td>
                    {alert.triggered ? (
                      <span className="flex-row items-center gap-xs text-neutral font-bold" style={{ fontSize: '0.75rem' }}>
                        <AlertTriangle size={13} /> Triggered {alert.lastTriggeredAt ? new Date(alert.lastTriggeredAt).toLocaleTimeString() : ''}
                      </span>
                    ) : (
                      <span className="text-muted" style={{ fontSize: '0.75rem' }}>Standing by (Monitoring)</span>
                    )}
                  </td>
                  <td>
                    <div className="flex-row items-center gap-sm">
                      <button
                        className="btn-ghost"
                        onClick={() => handleToggle(alert.id)}
                        style={{ color: alert.enabled ? 'var(--bullish)' : 'var(--text-muted)' }}
                        title={alert.enabled ? "Disable alert" : "Enable alert"}
                      >
                        <Power size={14} />
                      </button>
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
              ))
            )}
          </tbody>
        </table>
      </div>
    </div>
  );
}
