/*
 * ==================================================================================
 * FILE: Instrument.java
 * ==================================================================================
 *
 * WHAT THIS FILE DOES:
 * An "Instrument" is the PROFILE of a stock — all the permanent information
 * about it that doesn't change with every trade.
 *
 * Think of it like an ID card for a stock:
 *   - Name: "Apple Inc."
 *   - Ticker: "AAPL"
 *   - Exchange: "NASDAQ"
 *   - Currency: "USD"
 *   - Sector: "Technology"
 *   - Starting Price: $338.00
 *
 * WHERE IT'S USED:
 * - InstrumentRegistry.java stores a full list of all 283 instruments
 *   (233 Indian NSE stocks for simulation + 50 US stocks for live mode)
 * - The frontend uses this data to display company names, exchange badges,
 *   currency symbols, and sector filters
 *
 * The "basePrice" is the realistic starting price used in simulation mode.
 * For example, Reliance starts at ₹2900, TCS at ₹4100, etc.
 * In live mode, the actual real-time price from Finnhub replaces this.
 * ==================================================================================
 */

package com.quantstream.backend.domain;

import java.math.BigDecimal;

/**
 * Validated instrument definition in the QuantStream market universe.
 *
 * @param symbol      Ticker symbol (e.g., RELIANCE, TCS, AAPL)
 * @param companyName Human-readable company name
 * @param exchange    Trading venue (e.g., NSE, NASDAQ, NYSE)
 * @param currency    Currency symbol/code (e.g., INR, USD)
 * @param sector      Business sector classification
 * @param basePrice   Realistic starting reference price for simulation
 */
public record Instrument(
        String symbol,
        String companyName,
        String exchange,
        String currency,
        String sector,
        BigDecimal basePrice
) {}
