'use client';

import React from 'react';

export default function ConvictionScoreGauge({ snapshot }) {
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

  // Math for SVG radial arc
  const cx = 150;
  const cy = 135;
  const radius = 95;
  const strokeWidth = 14;
  const startAngle = 140;
  const endAngle = 400;

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

  const trendScore = factorScores.trend != null ? Number(factorScores.trend) : (scoreBreakdown.trend != null ? Number(scoreBreakdown.trend) * 4 : 50.0);
  const momentumScore = factorScores.momentum != null ? Number(factorScores.momentum) : (scoreBreakdown.momentum != null ? Number(scoreBreakdown.momentum) * 4 : 50.0);
  const rsiScore = factorScores.rsi != null ? Number(factorScores.rsi) : (scoreBreakdown.rsi != null ? Number(scoreBreakdown.rsi) * 4 : 50.0);
  const volumeScore = factorScores.volume != null ? Number(factorScores.volume) : (scoreBreakdown.volume != null ? Number(scoreBreakdown.volume) * 4 : 50.0);

  const trendContr = scoreBreakdown.trend != null ? Number(scoreBreakdown.trend) : trendScore * 0.25;
  const momentumContr = scoreBreakdown.momentum != null ? Number(scoreBreakdown.momentum) : momentumScore * 0.25;
  const rsiContr = scoreBreakdown.rsi != null ? Number(scoreBreakdown.rsi) : rsiScore * 0.25;
  const volumeContr = scoreBreakdown.volume != null ? Number(scoreBreakdown.volume) : volumeScore * 0.25;

  const factors = [
    { name: 'Trend', score: trendScore, contr: trendContr, color: 'var(--accent-blue)' },
    { name: 'Momentum', score: momentumScore, contr: momentumContr, color: 'var(--accent-indigo)' },
    { name: 'RSI', score: rsiScore, contr: rsiContr, color: 'var(--bullish)' },
    { name: 'Volume', score: volumeScore, contr: volumeContr, color: 'var(--neutral)' }
  ];

  return (
    <div className="terminal-panel flex-col items-center p-xl relative">
      <div className="w-full flex-row justify-between items-center mb-xs">
        <div className="flex-row items-center gap-xs">
          <span className="material-symbols-outlined text-accent" style={{ fontSize: '18px' }}>psychology</span>
          <h3 className="section-title">CONTINUOUS CONVICTION</h3>
        </div>
        <span className="badge badge-pill" style={{ fontSize: '0.68rem', background: 'rgba(255,255,255,0.05)' }}>
          4-FACTOR MODEL
        </span>
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
          {factors.map((f) => (
            <div key={f.name} className="flex-row justify-between items-center" style={{ fontSize: '0.78rem' }}>
              <div className="flex-row items-center gap-xs">
                <span style={{ width: '6px', height: '6px', borderRadius: '50%', background: f.color }} />
                <span className="text-secondary">{f.name}</span>
              </div>
              <div className="flex-row items-center gap-md">
                <span className="mono font-bold text-primary">{f.score.toFixed(1)}</span>
                <span className="mono text-muted" style={{ fontSize: '0.7rem', width: '52px', textAlign: 'right' }}>
                  +{f.contr.toFixed(1)} pts
                </span>
              </div>
            </div>
          ))}
        </div>
      </div>
    </div>
  );
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
