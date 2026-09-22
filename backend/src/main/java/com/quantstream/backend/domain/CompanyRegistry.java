/*
 * ==================================================================================
 * FILE: CompanyRegistry.java
 * ==================================================================================
 *
 * WHAT THIS FILE DOES:
 * This is a simple helper (a "facade") that maps stock ticker symbols to their
 * full, human-friendly company names.
 *
 * For example:
 *   - "AAPL"  -> "Apple Inc."
 *   - "RELIANCE" -> "Reliance Industries Limited"
 *   - "TSLA"  -> "Tesla Inc."
 *
 * HOW IT WORKS:
 * Instead of keeping its own duplicate list of company names, it simply forwards
 * (delegates) all requests to the master "InstrumentRegistry" phonebook.
 * ==================================================================================
 */

package com.quantstream.backend.domain;

/**
 * Registry mapping stock ticker symbols to human-readable company names.
 * Delegates directly to the validated {@link InstrumentRegistry}.
 */
public final class CompanyRegistry {

    // Private constructor so nobody accidentally creates an object: "new CompanyRegistry()"
    private CompanyRegistry() {}

    /**
     * Given a stock ticker symbol (e.g., "AAPL"), returns its readable name (e.g., "Apple Inc.").
     * If the ticker isn't recognized, it returns a safe fallback like "AAPL Corp".
     */
    public static String getCompanyName(String symbol) {
        return InstrumentRegistry.getCompanyName(symbol);
    }
}
