package com.quantstream.backend.domain;

/**
 * Registry mapping stock ticker symbols to human-readable company names.
 * Delegates directly to the validated {@link InstrumentRegistry}.
 */
public final class CompanyRegistry {

    private CompanyRegistry() {}

    public static String getCompanyName(String symbol) {
        return InstrumentRegistry.getCompanyName(symbol);
    }
}
