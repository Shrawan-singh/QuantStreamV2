/**
 * ==============================================================================
 * Centralized Market Utility Functions (frontend/src/lib/marketUtils.js)
 * ==============================================================================
 *
 * WHAT IS THIS FILE FOR? (Plain English):
 * When our dashboard displays stocks on screen, it needs to know:
 * 1. Is this a US stock (priced in Dollars '$', like Apple or Tesla) or
 *    an Indian stock (priced in Rupees '₹', like Reliance or TCS)?
 * 2. What little badge should appear next to the stock name? ('US EQ' or 'NSE EQ')?
 *
 * WHY A UTILITY FILE?
 * In programming, a "Utility" (or helper) file is like a Swiss Army knife.
 * Instead of copy-pasting the same currency-checking logic into 10 different
 * screen buttons and tables, we write the rules ONCE here and import them
 * wherever needed. If we ever want to add European stocks (€), we only have
 * to change this single file!
 * ==============================================================================
 */

/**
 * A quick lookup table (Set) of well-known mega-cap US equity ticker symbols.
 * A JavaScript `Set` allows lightning-fast checks (O(1) time complexity)
 * via `.has(symbol)`.
 */
const US_EQUITY_SYMBOLS = new Set([
  'AAPL', 'MSFT', 'AMZN', 'NVDA', 'GOOGL', 'META', 'TSLA',
]);

/**
 * Checks whether a given stock represents a US company or an Indian company.
 *
 * DECISION LOGIC (Three lines of defense):
 * 1. If the stock has an explicit `source === 'LIVE_PROVIDER'`, we know Finnhub
 *    streamed it from the New York Stock Exchange / NASDAQ -> True.
 * 2. If the stock symbol matches our known US list (e.g., 'AAPL') -> True.
 * 3. Fallback: If the global market configuration says `mode === 'live'` -> True.
 * 4. Otherwise, assume it is an Indian NSE stock -> False.
 *
 * @param {Object} stock - The stock data snapshot received from the backend
 * @param {Object} marketConfig - The app's current mode config ({ mode: 'live' | 'simulation' })
 * @returns {boolean} True if US equity (USD), False if Indian equity (INR)
 */
export function isUsEquityStock(stock, marketConfig) {
  if (!stock) return marketConfig?.mode === 'live';
  if (stock.source === 'LIVE_PROVIDER') return true;
  if (US_EQUITY_SYMBOLS.has(stock.symbol)) return true;
  return marketConfig?.mode === 'live';
}

/**
 * Returns the proper currency symbol ('$' or '₹') for formatting prices.
 *
 * Example:
 *   currencySymbol(appleStock) => '$'   (so we show "$182.50")
 *   currencySymbol(relianceStock) => '₹' (so we show "₹2,450.00")
 */
export function currencySymbol(stock, marketConfig) {
  return isUsEquityStock(stock, marketConfig) ? '$' : '₹';
}

/**
 * Returns the descriptive pill badge text shown in cards and tables.
 *
 * Example:
 *   exchangeBadge(appleStock) => 'US EQ' (US Equities)
 *   exchangeBadge(tcsStock) => 'NSE EQ' (National Stock Exchange Equities)
 */
export function exchangeBadge(stock, marketConfig) {
  return isUsEquityStock(stock, marketConfig) ? 'US EQ' : 'NSE EQ';
}

/**
 * Returns a short exchange tag suitable for compact labels or chart headers.
 *
 * Example:
 *   exchangeTag(appleStock) => 'US'
 *   exchangeTag(tcsStock) => 'NSE'
 */
export function exchangeTag(stock, marketConfig) {
  return isUsEquityStock(stock, marketConfig) ? 'US' : 'NSE';
}

