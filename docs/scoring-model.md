# QuantStream Quantitative Scoring Model Specification

## 1. Technical Indicators Specification

QuantStream computes five primary quantitative indicators from in-memory market state windows.

### 1. Simple Moving Average (SMA)
- **Mathematical Definition**:
  $$\text{SMA}_t = \frac{1}{N} \sum_{i=0}^{N-1} P_{t-i}$$
- **Default Window ($N$)**: 20 periods
- **Warm-up Criterion**: Requires at least $N$ prices in memory. Returns `NOT_READY` otherwise.
- **Signal Interpretation**:
  - `POSITIVE`: Latest Price $> \text{SMA} \times 1.002$ ($> 0.2\%$ above SMA)
  - `NEGATIVE`: Latest Price $< \text{SMA} \times 0.998$ ($> 0.2\%$ below SMA)
  - `NEUTRAL`: Latest Price within $\pm 0.2\%$ band around SMA.

### 2. Exponential Moving Average (EMA)
- **Mathematical Definition**:
  $$k = \frac{2}{N + 1}$$
  $$\text{EMA}_t = (P_t \times k) + (\text{EMA}_{t-1} \times (1 - k))$$
  - Initial seed: SMA of first $N$ prices.
- **Default Window ($N$)**: 20 periods
- **Signal Interpretation**:
  - `POSITIVE`: Latest Price $> \text{EMA} \times 1.002$
  - `NEGATIVE`: Latest Price $< \text{EMA} \times 0.998$
  - `NEUTRAL`: Latest Price within $\pm 0.2\%$ band around EMA.

### 3. Relative Strength Index (RSI)
- **Mathematical Definition** (Wilder's Smoothed Formulation):
  $$\text{RS} = \frac{\text{Smoothed Avg Gain}}{\text{Smoothed Avg Loss}}$$
  $$\text{RSI} = 100 - \frac{100}{1 + \text{RS}}$$
- **Default Window ($N$)**: 14 periods
- **Warm-up Criterion**: Requires at least $N + 1$ prices in memory (to calculate $N$ inter-tick delta values).
- **Signal Interpretation**:
  - `POSITIVE`: $\text{RSI} > 60.0$ (Bullish upward momentum)
  - `NEGATIVE`: $\text{RSI} < 40.0$ (Bearish downward momentum)
  - `NEUTRAL`: $40.0 \le \text{RSI} \le 60.0$

### 4. Price Momentum Rate of Change
- **Mathematical Definition**:
  $$\text{Momentum} = \left(\frac{P_t - P_{t-N}}{P_{t-N}}\right) \times 100$$
- **Default Lookback ($N$)**: 10 periods
- **Signal Interpretation**:
  - `POSITIVE`: Momentum $> +0.5\%$
  - `NEGATIVE`: Momentum $< -0.5\%$
  - `NEUTRAL`: $-0.5\% \le \text{Momentum} \le +0.5\%$

### 5. Relative Volume (RVOL)
- **Mathematical Definition**:
  $$\text{RVOL} = \frac{\text{Current Volume}}{\text{Average Volume over Lookback } N}$$
- **Default Lookback ($N$)**: 20 periods
- **Signal Interpretation**:
  - `POSITIVE`: $\text{RVOL} > 1.5\times$ (Unusually elevated market interest)
  - `NEGATIVE`: $\text{RVOL} < 0.7\times$ (Subdued trading activity / thin liquidity)
  - `NEUTRAL`: $0.7\times \le \text{RVOL} \le 1.5\times$

---

## 2. Conviction Score Synthesis (0–100 Scale)

The Conviction Score combines individual quantitative signals into an aggregated, transparent metric.

### Weighting Scheme
- **Trend Weight ($W_{\text{trend}}$)**: 25.0
- **Momentum Weight ($W_{\text{mom}}$)**: 25.0
- **RSI Weight ($W_{\text{rsi}}$)**: 25.0
- **Volume Weight ($W_{\text{vol}}$)**: 25.0
- **Total Weight ($W_{\text{total}}$)**: $25 + 25 + 25 + 25 = 100.0$

### Signal Ratio Mapping
Each indicator signal direction maps deterministically to a numerical multiplier ratio:
- `POSITIVE`: $1.0$ (Full bullish contribution, e.g. $+25$ pts)
- `NEUTRAL`: $0.5$ (Baseline neutral contribution, e.g. $+12.5$ pts)
- `NEGATIVE`: $0.0$ (Bearish / zero contribution, e.g. $+0.0$ pts)
- `NOT_READY`: $0.5$ (Neutral anchor during warm-up phase)

### Component Formula
$$\text{Score}_{\text{component}} = \left(\frac{W_{\text{component}}}{W_{\text{total}}}\right) \times 100 \times \text{Ratio}_{\text{signal}}$$

$$\text{ConvictionScore} = \text{clamp}\left(\sum \text{Score}_{\text{component}}, 0.0, 100.0\right)$$

### Qualitative Categorization

| Score Range | Category | Market Interpretation |
|---|---|---|
| **0 – 20** | `VERY_WEAK` | Bearish alignment across trend, momentum, and volume |
| **21 – 40** | `WEAK` | Unfavorable bias, multiple negative indicators |
| **41 – 60** | `NEUTRAL` | Mixed signals, consolidation, or initial warm-up |
| **61 – 80** | `STRONG` | Favorable momentum and trend confirmation |
| **81 – 100** | `VERY_STRONG` | Strong bullish confluence across all metrics |

### Edge-Case Mathematical Guarantees
1. **Flat Prices ($P_t = P_{t-1} = \dots = P_{t-N}$)**:
   - When prices remain completely flat across the lookback window, average gain $= 0.0$ and average loss $= 0.0$. Standard formulas divide by zero; QuantStream's implementation explicitly handles this by evaluating $\text{RSI} = 50.0$ with a `NEUTRAL` signal.
2. **Zero Price Movement in Momentum**:
   - If price change is exactly $0.0$, momentum evaluates to $0.0\%$ with a `NEUTRAL` signal.
3. **Zero Volume Baseline in RVOL**:
   - If historical average volume is $0$, relative volume evaluates as `NOT_READY` with fallback ratio $0.5$ (neutral), avoiding arithmetic `NaN` / divide-by-zero errors.
4. **Band-Based Trend Signal Suppression**:
   - Rather than flipping between Bullish and Bearish on microscopic fraction-of-a-cent noise, SMA and EMA enforce a $\pm 0.2\%$ neutral hysteresis band ($1.002$ / $0.998$).

---

## 3. Financial Disclaimer & Academic Purpose

> [!IMPORTANT]
> **Financial Safety Notice**: QuantStream provides quantitative analytical metrics for academic and decision-support demonstration. Indicators and Conviction Scores are not investment advice and do not guarantee future stock price performance.
