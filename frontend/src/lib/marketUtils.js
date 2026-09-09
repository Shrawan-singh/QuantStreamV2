/**
 * Centralized market utility functions.
 * Eliminates repeated hardcoded US‑equity lists scattered across components.
 */

const US_EQUITY_SYMBOLS = new Set([
  'AAPL', 'MSFT', 'AMZN', 'NVDA', 'GOOGL', 'META', 'TSLA',
]);

/**
 * Returns true when the given stock snapshot originates from a US equity feed.
 * Detection order: explicit source field → known US symbol → market config mode.
 */
export function isUsEquityStock(stock, marketConfig) {
  if (!stock) return marketConfig?.mode === 'live';
  if (stock.source === 'LIVE_PROVIDER') return true;
  if (US_EQUITY_SYMBOLS.has(stock.symbol)) return true;
  return marketConfig?.mode === 'live';
}

/** Returns the currency symbol for the given stock. */
export function currencySymbol(stock, marketConfig) {
  return isUsEquityStock(stock, marketConfig) ? '$' : '₹';
}

/** Returns the exchange badge label for the given stock. */
export function exchangeBadge(stock, marketConfig) {
  return isUsEquityStock(stock, marketConfig) ? 'US EQ' : 'NSE EQ';
}

/** Returns the short exchange tag (US / NSE). */
export function exchangeTag(stock, marketConfig) {
  return isUsEquityStock(stock, marketConfig) ? 'US' : 'NSE';
}
