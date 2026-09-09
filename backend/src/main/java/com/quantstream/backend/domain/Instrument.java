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
