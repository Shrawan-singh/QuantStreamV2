'use client';

/**
 * ==============================================================================
 * Conviction Score Gauge & Explainability View (frontend/src/components/ConvictionScoreGauge.jsx)
 * ==============================================================================
 *
 * WHAT IS THIS COMPONENT FOR? (Plain English):
 * Have you ever looked at a car's speedometer? It has a curved dial with a needle
 * that sweeps from 0 to 100.
 *
 * This component draws a high-tech SVG radial dial for a stock's Conviction Score:
 * - 0 to 30 (Red): VERY WEAK / Bearish conviction (signals suggest price may fall)
 * - 30 to 45 (Orange): WEAK
 * - 45 to 55 (Yellow/Amber): NEUTRAL / Sideways consolidation
 * - 55 to 70 (Cyan): STRONG
 * - 70 to 100 (Emerald Green): VERY STRONG / Bullish conviction
 *
 * WHY "EXPLAINABLE"?
 * Many black-box trading algorithms give a random number without explaining WHY.
 * QuantStream is designed to be 100% transparent and explainable:
 * 1. Shows exactly how much each of the 4 factors contributed (Trend + Momentum + RSI + Volume = Total).
 * 2. Provides plain-English bullet points explaining the mathematical evidence
 *    behind the score (e.g. "RSI is in healthy accumulation zone at 58.2").
 * ==============================================================================
 */

import React, { useState } from 'react';

export default function ConvictionScoreGauge({ snapshot, showBreakdownInitial = false }) {
  // Toggle state: allows user to expand or collapse detailed factor explanations
  const [showBreakdown, setShowBreakdown] = useState(showBreakdownInitial);

  // If no stock data has arrived from the server yet, show a clean empty state
  if (!snapshot) {
    return (
      <div className="terminal-panel p-xl flex-col items-center justify-center text-center" style={{ minHeight: '300px' }}>
        <p className="text-muted">Awaiting Market Data</p>
      </div>
    );
  }

  const score = snapshot.convictionScore ?? 50;
  const category = snapshot.scoreCategory ?? 'NEUTRAL';
  const scoreColor = getCategoryColor(category);

  // --- SVG RADIAL ARC GEOMETRY ---
  // In computer graphics, drawing a circular curved arc requires converting polar
  // coordinates (angles and radius) into Cartesian coordinates (X and Y pixel coordinates).
  const cx = 150;          // Center X position in pixels
  const cy = 135;          // Center Y position in pixels
  const radius = 95;       // Arc radius
  const strokeWidth = 14;  // Thickness of the curved bar
  const startAngle = 140;  // Where the gauge starts (bottom-left)
  const endAngle = 400;    // Where the gauge ends (bottom-right)


  const polarToCartesian = (cx, cy, r, angleInDegrees) => {
    const angleInRadians = (angleInDegrees - 90) * Math.PI / 180.0;
    return {
      x: cx + (r * Math.cos(angleInRadians)),
      y: cy + (r * Math.sin(angleInRadians))
    };
  };

  const describeArc = (x, y, r, startAngle, endAngle) => {
    const start = polarToCartesian(x, y, r, endAngle);
    const end = polarToCartesian(x, y, r, startAngle);
    const largeArcFlag = endAngle - startAngle <= 180 ? '0' : '1';
    return [
      'M', start.x, start.y, 
      'A', r, r, 0, largeArcFlag, 0, end.x, end.y
    ].join(' ');
  };

  const scoreAngle = startAngle + (Math.min(100, Math.max(0, score)) / 100) * (endAngle - startAngle);
  const trackPath = describeArc(cx, cy, radius, startAngle, endAngle);
  const fillPath = describeArc(cx, cy, radius, startAngle, scoreAngle);

  // Sub-scores and weighted contributions (0.25 each)
  const factorScores = snapshot.factorScores || {};
  const scoreBreakdown = snapshot.scoreBreakdown || {};
  const signals = snapshot.signals || {};

  const trendScore = factorScores.trend != null ? Number(factorScores.trend) : (scoreBreakdown.trend != null ? Number(scoreBreakdown.trend) * 4 : 50.0);
  const momentumScore = factorScores.momentum != null ? Number(factorScores.momentum) : (scoreBreakdown.momentum != null ? Number(scoreBreakdown.momentum) * 4 : 50.0);
  const rsiScore = factorScores.rsi != null ? Number(factorScores.rsi) : (scoreBreakdown.rsi != null ? Number(scoreBreakdown.rsi) * 4 : 50.0);
  const volumeScore = factorScores.volume != null ? Number(factorScores.volume) : (scoreBreakdown.volume != null ? Number(scoreBreakdown.volume) * 4 : 50.0);

  const trendContr = scoreBreakdown.trend != null ? Number(scoreBreakdown.trend) : trendScore * 0.25;
  const momentumContr = scoreBreakdown.momentum != null ? Number(scoreBreakdown.momentum) : momentumScore * 0.25;
  const rsiContr = scoreBreakdown.rsi != null ? Number(scoreBreakdown.rsi) : rsiScore * 0.25;
  const volumeContr = scoreBreakdown.volume != null ? Number(scoreBreakdown.volume) : volumeScore * 0.25;

  const factors = [
    { key: 'trend', name: 'Trend', score: trendScore, contr: trendContr, color: 'var(--accent-blue)' },
    { key: 'momentum', name: 'Momentum', score: momentumScore, contr: momentumContr, color: 'var(--accent-indigo)' },
    { key: 'rsi', name: 'RSI', score: rsiScore, contr: rsiContr, color: 'var(--bullish)' },
    { key: 'volume', name: 'Volume', score: volumeScore, contr: volumeContr, color: 'var(--neutral)' }
  ];

  const hasExplanations = snapshot.explanations && snapshot.explanations.length > 0;

  return (
    <div className="terminal-panel flex-col items-center p-xl relative" style={{ width: '100%' }}>
      <div className="w-full flex-row justify-between items-center mb-xs">
        <div className="flex-row items-center gap-xs">
          <span className="material-symbols-outlined text-accent" style={{ fontSize: '18px' }}>psychology</span>
          <h3 className="section-title">CONTINUOUS CONVICTION</h3>
        </div>
        <div className="flex-row items-center gap-xs">
          <button
            type="button"
            className="btn-ghost flex-row items-center gap-2xs"
            onClick={() => setShowBreakdown(!showBreakdown)}
            style={{ fontSize: '0.68rem', color: 'var(--accent-blue)', padding: '2px 8px', borderRadius: '3px' }}
          >
            {showBreakdown ? 'HIDE DETAIL ▲' : 'FACTOR DETAIL ▸'}
          </button>
          <span className="badge badge-pill" style={{ fontSize: '0.68rem', background: 'rgba(255,255,255,0.05)' }}>
            4-FACTOR MODEL
          </span>
        </div>
      </div>

      <div className="relative" style={{ width: '280px', height: '190px' }}>
        {/* Ambient glow */}
        <div style={{
          position: 'absolute',
          top: '45%',
          left: '50%',
          transform: 'translate(-50%, -50%)',
          width: '140px',
          height: '140px',
          borderRadius: '50%',
          background: scoreColor,
          filter: 'blur(50px)',
          opacity: 0.18,
          pointerEvents: 'none'
        }} />

        <svg width="280" height="190" viewBox="0 0 300 210" style={{ position: 'relative', zIndex: 1 }}>
          <defs>
            <linearGradient id="scoreGradient" x1="0%" y1="0%" x2="100%" y2="0%">
              <stop offset="0%" stopColor={scoreColor} stopOpacity="0.5" />
              <stop offset="100%" stopColor={scoreColor} stopOpacity="1" />
            </linearGradient>
          </defs>

          {/* Background Track */}
          <path
            d={trackPath}
            fill="none"
            stroke="rgba(255, 255, 255, 0.06)"
            strokeWidth={strokeWidth}
            strokeLinecap="round"
          />

          {/* Animated Fill */}
          <path
            d={fillPath}
            fill="none"
            stroke="url(#scoreGradient)"
            strokeWidth={strokeWidth}
            strokeLinecap="round"
            style={{ transition: 'd 0.6s cubic-bezier(0.2, 0.8, 0.2, 1)' }}
          />

          {/* Center Text */}
          <text x={cx} y={cy - 5} textAnchor="middle" fill="var(--text-primary)" fontSize="44" fontWeight="800" fontFamily="var(--font-mono)">
            {Number(score).toFixed(1)}
          </text>
          <text x={cx} y={cy + 24} textAnchor="middle" fill={scoreColor} fontSize="13" fontWeight="700" letterSpacing="0.06em">
            {category.replace('_', ' ')}
          </text>
        </svg>
      </div>

      {/* Factor Sub-Score & Explainability Table */}
      <div className="w-full mt-sm pt-sm" style={{ borderTop: '1px solid var(--border-subtle)' }}>
        <div className="flex-row justify-between items-center mb-xs">
          <span className="label-caps" style={{ fontSize: '0.68rem' }}>FACTOR EXPLAINABILITY</span>
          <span className="text-muted mono" style={{ fontSize: '0.65rem' }}>SCORE (WEIGHT: 25%)</span>
        </div>

        <div className="flex-col gap-xs">
          {factors.map((f) => {
            const sig = signals[f.key] || 'NEUTRAL';
            const sigStyle = getSignalStyle(sig);

            return (
              <div key={f.name} className="flex-row justify-between items-center" style={{ fontSize: '0.78rem' }}>
                <div className="flex-row items-center gap-xs">
                  <span style={{ width: '6px', height: '6px', borderRadius: '50%', background: f.color }} />
                  <span className="text-secondary">{f.name}</span>
                  {showBreakdown && (
                    <span
                      className="badge"
                      style={{
                        fontSize: '0.62rem',
                        padding: '1px 5px',
                        color: sigStyle.color,
                        background: sigStyle.bg,
                        border: `1px solid ${sigStyle.border}`,
                        marginLeft: '4px'
                      }}
                    >
                      {sig}
                    </span>
                  )}
                </div>
                <div className="flex-row items-center gap-sm">
                  {showBreakdown && (
                    <div className="score-bar" style={{ width: '50px', height: '4px' }}>
                      <div
                        className="score-bar-fill"
                        style={{
                          width: `${Math.min(100, Math.max(0, f.score))}%`,
                          backgroundColor: f.color,
                        }}
                      />
                    </div>
                  )}
                  <span className="mono font-bold text-primary">{f.score.toFixed(1)}</span>
                  <span className="mono text-muted" style={{ fontSize: '0.7rem', width: '52px', textAlign: 'right' }}>
                    +{f.contr.toFixed(1)} pts
                  </span>
                </div>
              </div>
            );
          })}
        </div>
      </div>

      {/* Expandable Factor Breakdown & Explanation Strings */}
      {showBreakdown && hasExplanations && (
        <div className="w-full mt-md pt-sm" style={{ borderTop: '1px solid var(--border-subtle)' }}>
          <div className="flex-row justify-between items-center mb-xs">
            <span className="label-caps" style={{ fontSize: '0.68rem', color: 'var(--accent-blue)' }}>
              MODEL RATIONALE & INDICATOR EXPLANATION
            </span>
          </div>
          <div className="flex-col gap-xs" style={{ maxHeight: '180px', overflowY: 'auto', paddingRight: '4px' }}>
            {snapshot.explanations.map((exp, idx) => (
              <div
                key={idx}
                className="flex-row items-start gap-xs"
                style={{
                  fontSize: '0.72rem',
                  lineHeight: '1.4',
                  padding: '4px 6px',
                  borderRadius: '3px',
                  background: 'rgba(255, 255, 255, 0.02)',
                  border: '1px solid rgba(255, 255, 255, 0.04)'
                }}
              >
                <span style={{ color: 'var(--accent-blue)', marginTop: '1px' }}>▸</span>
                <span className="text-secondary">{exp}</span>
              </div>
            ))}
          </div>
        </div>
      )}
    </div>
  );
}

function getSignalStyle(sig) {
  switch (sig) {
    case 'POSITIVE':
      return { color: 'var(--bullish)', bg: 'var(--bullish-bg)', border: 'rgba(16, 185, 129, 0.3)' };
    case 'NEGATIVE':
      return { color: 'var(--bearish)', bg: 'var(--bearish-bg)', border: 'rgba(239, 68, 68, 0.3)' };
    case 'NOT_READY':
      return { color: 'var(--text-muted)', bg: 'rgba(255,255,255,0.05)', border: 'rgba(255,255,255,0.1)' };
    default:
      return { color: 'var(--neutral)', bg: 'var(--neutral-bg)', border: 'rgba(245, 158, 11, 0.3)' };
  }
}

function getCategoryColor(cat) {
  switch (cat) {
    case 'VERY_STRONG': return 'var(--score-very-strong)';
    case 'STRONG':      return 'var(--score-strong)';
    case 'NEUTRAL':     return 'var(--score-neutral)';
    case 'WEAK':        return 'var(--score-weak)';
    case 'VERY_WEAK':   return 'var(--score-very-weak)';
    default:            return 'var(--score-neutral)';
  }
}
