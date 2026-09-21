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

## 2. Volatility-Relative Conviction Score Synthesis (0–100 Scale)

The Conviction Score synthesizes quantitative indicators into a statistically standardized, volatility-relative conviction metric with EMA confirmation, exponential smoothing, and category hysteresis.

### A. Realized Volatility Calculation
Realized volatility is computed over a rolling window ($N = 20$ ticks, minimum required $k = 5$ ticks) using the sample standard deviation of log returns:
$$r_t = \ln\left(\frac{P_t}{P_{t-1}}\right)$$
$$\bar{r} = \frac{1}{k} \sum_{i=1}^{k} r_{t-i+1}$$
$$\sigma_{\text{realized}} = \sqrt{\frac{1}{k-1} \sum_{i=1}^{k} (r_{t-i+1} - \bar{r})^2}$$

If fewer than $k$ return samples are available, realized volatility is flagged as insufficient ($-1.0$), and trend/momentum anchor safely at a neutral $50.0$.
If all prices in the window are identical ($\sigma_{\text{realized}} = 0.0$), the score engine uses an exact zero or applies a minimal floor ($\sigma_{\text{floor}} = 0.005$) if a small price divergence occurs.

---

### B. Volatility-Relative Trend Factor
Rather than comparing price to moving averages against arbitrary fixed percentage thresholds, the trend is measured as a Z-score relative to the stock's own realized return volatility:
$$\Delta_{\text{SMA}} = \frac{P_t - \text{SMA}_t}{\text{SMA}_t}$$
$$Z_{\text{trend}} = \frac{\Delta_{\text{SMA}}}{\sigma_{\text{realized}}}$$

With a default cap $Z_{\max} = 3.0$:
$$\text{BaseTrendScore} = \text{clamp}\left(50.0 + \frac{Z_{\text{trend}}}{Z_{\max}} \times 50.0, \; 0.0, \; 100.0\right)$$

#### EMA Dual-Confirmation Adjustment
- **Dual Bullish Agreement** ($P_t > \text{SMA}$ and $P_t > \text{EMA}$): $+4.0$ bonus
- **Dual Bearish Agreement** ($P_t < \text{SMA}$ and $P_t < \text{EMA}$): $-4.0$ deduction
- **Divergence / Mixed Signal** (e.g. $P_t > \text{SMA}$ but $P_t < \text{EMA}$): $-3.0$ penalty pulling toward $50.0$ neutral.

$$\text{TrendScore} = \text{clamp}\left(\text{BaseTrendScore} + \text{Adjustment}, \; 0.0, \; 100.0\right)$$

---

### C. Volatility-Relative Momentum Factor
Momentum rate-of-change ($R_{\text{lookback}} = \frac{P_t - P_{t-N}}{P_{t-N}}$) is evaluated as a Z-score normalized by realized volatility:
$$Z_{\text{mom}} = \frac{R_{\text{lookback}}}{\sigma_{\text{realized}}}$$
$$\text{MomentumScore} = \text{clamp}\left(50.0 + \frac{Z_{\text{mom}}}{Z_{\max}} \times 50.0, \; 0.0, \; 100.0\right)$$

Equivalence Guarantee: A low-volatility utility stock moving $+1.5\%$ ($\sigma = 0.5\%$) yields $Z = +3.0$ and achieves a maximum MomentumScore of $100.0$. A high-volatility tech stock moving $+15.0\%$ ($\sigma = 5.0\%$) similarly yields $Z = +3.0$ and achieves $100.0$.

---

### D. RSI and Relative Volume Factors
- **RSI Factor**: Mapped directly from Wilder's RSI:
  $$\text{RsiScore} = \text{clamp}(\text{RSI}, \; 0.0, \; 100.0)$$
- **Volume Factor**: Mapped continuously around baseline RVOL $= 1.0$:
  - $\text{RVOL} \ge 2.0 \implies 100.0$
  - $\text{RVOL} \le 0.5 \implies 0.0$
  - $0.5 < \text{RVOL} < 2.0 \implies$ Linear interpolation from $0.0$ to $100.0$.

---

### E. Weighted Raw Score & Exponential Smoothing
The raw composite score is computed across the four weighted components ($W_i = 25.0$ each, total $100.0$):
$$\text{RawScore}_t = \sum_{i=1}^{4} \left(\frac{W_i}{W_{\text{total}}}\right) \times \text{FactorScore}_i$$

To eliminate tick-to-tick noise while preserving real responsiveness, the score is smoothed via an exponential moving average ($\alpha = 0.20$):
$$S_t = \alpha \times \text{RawScore}_t + (1 - \alpha) \times S_{t-1}$$
For the initial tick or when warming up, $S_0 = \text{RawScore}_0$.

---

### F. Category Hysteresis
To prevent rapid visual flip-flopping across qualitative categories, a hysteresis buffer ($\pm 1.5$ points) is maintained around the 40.0 and 60.0 boundary thresholds:

| Current Category | Upgrade Condition | Downgrade Condition |
|---|---|---|
| `NEUTRAL` | $S_t > 60.0 + 1.5 = 61.5 \implies \text{STRONG}$ | $S_t < 40.0 - 1.5 = 38.5 \implies \text{WEAK}$ |
| `STRONG` | $S_t > 80.0 \implies \text{VERY\_STRONG}$ | $S_t < 60.0 - 1.5 = 58.5 \implies \text{NEUTRAL}$ |
| `WEAK` | $S_t > 40.0 + 1.5 = 41.5 \implies \text{NEUTRAL}$ | $S_t \le 20.0 \implies \text{VERY\_WEAK}$ |

---

### G. Natural Language Explainability
Each score evaluation generates human-readable audit bullets for each contributing factor, explicit mention of realized return volatility, Z-scores, EMA confirmation bonuses/penalties, smoothing weights, and current hysteresis state.

---

## 3. Financial Disclaimer & Academic Purpose

> [!IMPORTANT]
> **Financial Safety Notice**: QuantStream provides quantitative analytical metrics for academic and decision-support demonstration. Indicators and Conviction Scores are not investment advice and do not guarantee future stock price performance.
