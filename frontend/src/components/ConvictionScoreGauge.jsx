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
  const cy = 150;
  const radius = 110;
  const strokeWidth = 16;
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

  // Convert score (0-100) to an angle between startAngle and endAngle
  const scoreAngle = startAngle + (score / 100) * (endAngle - startAngle);
  const trackPath = describeArc(cx, cy, radius, startAngle, endAngle);
  const fillPath = describeArc(cx, cy, radius, startAngle, scoreAngle);

  // Coordinates for the tick marks
  const ticks = [0, 25, 50, 75, 100];
  const tickElements = ticks.map(t => {
    const a = startAngle + (t / 100) * (endAngle - startAngle);
    const inner = polarToCartesian(cx, cy, radius - strokeWidth/2 - 5, a);
    const outer = polarToCartesian(cx, cy, radius + strokeWidth/2 + 5, a);
    return (
      <line key={`tick-${t}`} x1={inner.x} y1={inner.y} x2={outer.x} y2={outer.y} stroke="rgba(255,255,255,0.2)" strokeWidth="2" />
    );
  });

  return (
    <div className="terminal-panel flex-col items-center p-xl relative">
      <div className="w-full flex-row justify-between items-center mb-md">
        <div className="flex-row items-center gap-xs">
          <span className="material-symbols-outlined text-accent" style={{ fontSize: '18px' }}>psychology</span>
          <h3 className="section-title">ALGORITHMIC CONVICTION</h3>
        </div>
      </div>

      <div className="relative" style={{ width: '300px', height: '240px' }}>
        {/* Glow effect */}
        <div style={{
          position: 'absolute',
          top: '50%',
          left: '50%',
          transform: 'translate(-50%, -50%)',
          width: '180px',
          height: '180px',
          borderRadius: '50%',
          background: scoreColor,
          filter: 'blur(60px)',
          opacity: 0.15,
          zIndex: 0,
          pointerEvents: 'none'
        }} />

        <svg width="300" height="240" viewBox="0 0 300 240" style={{ position: 'relative', zIndex: 1 }}>
          <defs>
            <linearGradient id="scoreGradient" x1="0%" y1="0%" x2="100%" y2="0%">
              <stop offset="0%" stopColor={scoreColor} stopOpacity="0.6" />
              <stop offset="100%" stopColor={scoreColor} stopOpacity="1" />
            </linearGradient>
            
            <filter id="glow" x="-20%" y="-20%" width="140%" height="140%">
              <feGaussianBlur stdDeviation="4" result="blur" />
              <feComposite in="SourceGraphic" in2="blur" operator="over" />
            </filter>
          </defs>

          {/* Background Track */}
          <path
            d={trackPath}
            fill="none"
            stroke="rgba(255, 255, 255, 0.05)"
            strokeWidth={strokeWidth}
            strokeLinecap="round"
          />

          {/* Ticks */}
          {tickElements}

          {/* Animated Fill Path */}
          <path
            d={fillPath}
            fill="none"
            stroke="url(#scoreGradient)"
            strokeWidth={strokeWidth}
            strokeLinecap="round"
            filter="url(#glow)"
            style={{ transition: 'd 0.8s cubic-bezier(0.2, 0.8, 0.2, 1)' }}
          />

          {/* Center Text Area */}
          <text x={cx} y={cy - 10} textAnchor="middle" fill="var(--text-primary)" fontSize="48" fontWeight="800" fontFamily="var(--font-mono)">
            {score.toFixed(1)}
          </text>
          <text x={cx} y={cy + 25} textAnchor="middle" fill={scoreColor} fontSize="14" fontWeight="700" letterSpacing="0.05em">
            {category.replace('_', ' ')}
          </text>
        </svg>

        {/* Legend */}
        <div className="absolute bottom-0 left-0 right-0 flex-row justify-center gap-md" style={{ bottom: '-10px' }}>
          <div className="flex-row items-center gap-xs">
            <div style={{ width: '8px', height: '8px', borderRadius: '50%', background: 'var(--score-very-weak)' }} />
            <span className="mono" style={{ fontSize: '0.65rem', color: 'var(--text-muted)' }}>0</span>
          </div>
          <div className="flex-row items-center gap-xs">
            <div style={{ width: '8px', height: '8px', borderRadius: '50%', background: 'var(--score-neutral)' }} />
            <span className="mono" style={{ fontSize: '0.65rem', color: 'var(--text-muted)' }}>50</span>
          </div>
          <div className="flex-row items-center gap-xs">
            <div style={{ width: '8px', height: '8px', borderRadius: '50%', background: 'var(--score-very-strong)' }} />
            <span className="mono" style={{ fontSize: '0.65rem', color: 'var(--text-muted)' }}>100</span>
          </div>
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
