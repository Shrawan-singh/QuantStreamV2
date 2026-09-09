'use client';

import React, { useState, useEffect, useRef } from 'react';
import { LineChart, Play } from 'lucide-react';
import { currencySymbol, exchangeTag } from '../lib/marketUtils';

export default function InteractiveChart({ symbol, currentPrice, currentSma, apiBase, isUsEquity }) {
  const [history, setHistory] = useState([]);
  const [loading, setLoading] = useState(true);
  const [hoverIndex, setHoverIndex] = useState(null);
  const containerRef = useRef(null);

  // We only fetch historical data when the symbol changes
  useEffect(() => {
    let mounted = true;
    const fetchHistory = async () => {
      if (!symbol) return;
      setLoading(true);
      try {
        const res = await fetch(`${apiBase || 'http://localhost:8080'}/api/stocks/${symbol}/history`);
        if (res.ok && mounted) {
          const data = await res.json();
          const normalized = (data || [])
            .slice()
            .reverse() // Backend returns DESC (newest first). Reverse to chronological ASC
            .map(item => ({
              timestamp: item.timestamp,
              closePrice: Number(item.closePrice ?? item.price ?? 0),
              sma20: (item.sma20 ?? item.sma) ? Number(item.sma20 ?? item.sma) : null,
            }))
            .filter(d => !isNaN(d.closePrice) && d.closePrice > 0);
          setHistory(normalized);
        }
      } catch (err) {
        console.debug('History fetch failed', err);
      } finally {
        if (mounted) setLoading(false);
      }
    };
    fetchHistory();
    return () => { mounted = false; };
  }, [symbol, apiBase]);

  // Combine fetched history with the latest real-time tick to draw the full chart
  const chartData = [...history];
  if (currentPrice != null && !isNaN(Number(currentPrice)) && Number(currentPrice) > 0) {
    chartData.push({
      timestamp: Date.now(),
      closePrice: Number(currentPrice),
      sma20: currentSma != null && !isNaN(Number(currentSma)) && Number(currentSma) > 0 ? Number(currentSma) : null,
    });
  }

  // Calculate scales and SVG paths
  const width = 800;
  const height = 300;
  const margin = { top: 20, right: 60, bottom: 30, left: 20 };
  const innerWidth = width - margin.left - margin.right;
  const innerHeight = height - margin.top - margin.bottom;

  let pricePath = '';
  let smaPath = '';
  let areaPath = '';
  let minP = 0, maxP = 1;

  if (chartData.length > 1) {
    const prices = chartData.map(d => d.closePrice).filter(p => p != null && !isNaN(p) && p > 0);
    const smas = chartData.map(d => d.sma20).filter(p => p != null && !isNaN(p) && p > 0);
    const allVals = prices.length > 0 ? [...prices, ...smas] : [100];
    
    minP = Math.min(...allVals) * 0.998;
    maxP = Math.max(...allVals) * 1.002;
    if (minP >= maxP) { minP -= 1; maxP += 1; }

    const range = maxP - minP || 1;
    const getX = (index) => margin.left + (index / (chartData.length - 1 || 1)) * innerWidth;
    const getY = (val) => margin.top + innerHeight - (((val || minP) - minP) / range) * innerHeight;

    const pricePoints = chartData.map((d, i) => `${getX(i)},${getY(d.closePrice)}`);
    pricePath = `M ${pricePoints.join(' L ')}`;

    areaPath = `${pricePath} L ${getX(chartData.length - 1)},${height - margin.bottom} L ${margin.left},${height - margin.bottom} Z`;

    const smaPoints = chartData.map((d, i) => (d.sma20 && d.sma20 > 0) ? `${getX(i)},${getY(d.sma20)}` : null).filter(Boolean);
    if (smaPoints.length > 0) {
      smaPath = `M ${smaPoints.join(' L ')}`;
    }
  }

  // Tooltip interaction
  const handleMouseMove = (e) => {
    if (!containerRef.current || chartData.length < 2) return;
    const rect = containerRef.current.getBoundingClientRect();
    const x = e.clientX - rect.left;
    
    // Convert x coordinate to array index
    const relativeX = x - margin.left;
    const percentage = relativeX / (innerWidth || 1);
    let index = Math.round(percentage * (chartData.length - 1));
    index = Math.max(0, Math.min(index, chartData.length - 1));
    setHoverIndex(index);
  };

  const curSym = isUsEquity ? '$' : '₹';

  return (
    <div className="terminal-panel p-xl">
      {/* Header */}
      <div className="flex-row items-center justify-between mb-lg">
        <div className="flex-row items-center gap-xs">
          <span className="material-symbols-outlined text-accent" style={{ fontSize: '18px' }}>ssid_chart</span>
          <h3 className="section-title">MARKET INTELLIGENCE CHART</h3>
        </div>
        <div className="flex-row items-center gap-sm">
          <div className="flex-row items-center gap-xs text-muted" style={{ fontSize: '0.75rem' }}>
            <div style={{ width: '12px', height: '2px', background: 'var(--accent-blue)' }} />
            <span>Price</span>
          </div>
          <div className="flex-row items-center gap-xs text-muted" style={{ fontSize: '0.75rem' }}>
            <div style={{ width: '12px', height: '2px', background: 'var(--accent-purple)' }} />
            <span>SMA (20)</span>
          </div>
        </div>
      </div>

      {loading ? (
        <div className="flex-col items-center justify-center text-muted" style={{ height: '300px' }}>
          <div className="skeleton w-full" style={{ height: '250px' }} />
        </div>
      ) : chartData.length < 2 ? (
        <div className="flex-col items-center justify-center text-muted" style={{ height: '300px' }}>
          <Play size={32} style={{ opacity: 0.2, marginBottom: '8px' }} />
          <p>Awaiting historical ticks...</p>
        </div>
      ) : (
        <div 
          ref={containerRef}
          style={{ position: 'relative', width: '100%', height: '100%', cursor: 'crosshair' }}
          onMouseMove={handleMouseMove}
          onMouseLeave={() => setHoverIndex(null)}
        >
          <svg viewBox={`0 0 ${width} ${height}`} style={{ width: '100%', height: 'auto', display: 'block', overflow: 'visible' }}>
            <defs>
              <linearGradient id="chartArea" x1="0" y1="0" x2="0" y2="1">
                <stop offset="0%" stopColor="var(--accent-blue)" stopOpacity="0.2" />
                <stop offset="100%" stopColor="var(--accent-blue)" stopOpacity="0.0" />
              </linearGradient>
            </defs>

            {/* Grid lines */}
            {[0, 0.25, 0.5, 0.75, 1].map((p, i) => {
              const y = margin.top + p * innerHeight;
              const val = maxP - p * (maxP - minP);
              return (
                <g key={`grid-${i}`}>
                  <line x1={margin.left} y1={y} x2={width - margin.right} y2={y} stroke="rgba(255,255,255,0.05)" strokeDasharray="4 4" />
                  <text x={width - margin.right + 8} y={y + 4} fill="var(--text-muted)" fontSize="10" fontFamily="var(--font-mono)">
                    {curSym}{(val != null && !isNaN(val) ? Number(val) : 0).toFixed(2)}
                  </text>
                </g>
              );
            })}

            {/* Area & Lines */}
            <path d={areaPath} fill="url(#chartArea)" />
            {smaPath && <path d={smaPath} fill="none" stroke="var(--accent-purple)" strokeWidth="1.5" strokeDasharray="4 4" />}
            <path d={pricePath} fill="none" stroke="var(--accent-blue)" strokeWidth="2" strokeLinejoin="round" strokeLinecap="round" />

            {/* Current Price Marker */}
            {(() => {
              const lastPoint = chartData[chartData.length - 1];
              if (!lastPoint || lastPoint.closePrice == null) return null;
              const range = maxP - minP || 1;
              const lastX = margin.left + innerWidth;
              const lastY = margin.top + innerHeight - (((lastPoint.closePrice || minP) - minP) / range) * innerHeight;
              return (
                <g>
                  <circle cx={lastX} cy={lastY} r="4" fill="var(--bg-card)" stroke="var(--accent-blue)" strokeWidth="2" />
                  <circle cx={lastX} cy={lastY} r="12" fill="var(--accent-blue)" opacity="0.2">
                    <animate attributeName="r" values="4;16;4" dur="2s" repeatCount="indefinite" />
                    <animate attributeName="opacity" values="0.4;0;0.4" dur="2s" repeatCount="indefinite" />
                  </circle>
                </g>
              );
            })()}

            {/* Crosshair & Tooltip */}
            {hoverIndex !== null && chartData[hoverIndex] && (
              (() => {
                const hData = chartData[hoverIndex];
                if (!hData || hData.closePrice == null) return null;
                const range = maxP - minP || 1;
                const hX = margin.left + (hoverIndex / (chartData.length - 1 || 1)) * innerWidth;
                const hY = margin.top + innerHeight - (((hData.closePrice || minP) - minP) / range) * innerHeight;
                const isLeftHalf = hX < width / 2;
                const pVal = Number(hData.closePrice || 0);
                const smaVal = (hData.sma20 != null && !isNaN(hData.sma20) && Number(hData.sma20) > 0) ? Number(hData.sma20) : null;
                
                return (
                  <g>
                    {/* Vertical & Horizontal Crosshair lines */}
                    <line x1={hX} y1={margin.top} x2={hX} y2={height - margin.bottom} stroke="rgba(255,255,255,0.2)" strokeDasharray="2 2" />
                    <line x1={margin.left} y1={hY} x2={width - margin.right} y2={hY} stroke="rgba(255,255,255,0.2)" strokeDasharray="2 2" />
                    
                    {/* Intersection Point */}
                    <circle cx={hX} cy={hY} r="4" fill="var(--accent-blue)" />
                    
                    {/* Tooltip Background (SVG grouping) */}
                    <g transform={`translate(${isLeftHalf ? hX + 15 : hX - 145}, ${Math.max(margin.top, hY - 60)})`}>
                      <rect width="130" height={smaVal != null ? 70 : 50} rx="6" fill="var(--bg-elevated)" stroke="var(--border-active)" />
                      <text x="10" y="20" fill="var(--text-muted)" fontSize="10" fontFamily="var(--font-sans)">
                        {hData.timestamp ? new Date(hData.timestamp).toLocaleTimeString() : ''}
                      </text>
                      <text x="10" y="40" fill="var(--text-primary)" fontSize="12" fontFamily="var(--font-mono)" fontWeight="700">
                        P: {curSym}{pVal.toFixed(2)}
                      </text>
                      {smaVal != null && (
                        <text x="10" y="58" fill="var(--accent-purple)" fontSize="12" fontFamily="var(--font-mono)" fontWeight="600">
                          SMA: {curSym}{smaVal.toFixed(2)}
                        </text>
                      )}
                    </g>
                  </g>
                );
              })()
            )}
          </svg>
        </div>
      )}
    </div>
  );
}
