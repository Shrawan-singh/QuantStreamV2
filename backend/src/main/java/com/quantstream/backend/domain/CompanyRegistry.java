package com.quantstream.backend.domain;

import java.util.Map;

/**
 * Registry mapping stock ticker symbols to human-readable company names.
 */
public final class CompanyRegistry {

    private static final Map<String, String> COMPANY_NAMES = Map.ofEntries(
            Map.entry("RELIANCE", "Reliance Industries Ltd"),
            Map.entry("TCS", "Tata Consultancy Services Ltd"),
            Map.entry("INFY", "Infosys Ltd"),
            Map.entry("HDFCBANK", "HDFC Bank Ltd"),
            Map.entry("ICICIBANK", "ICICI Bank Ltd"),
            Map.entry("SBIN", "State Bank of India"),
            Map.entry("BHARTIARTL", "Bharti Airtel Ltd"),
            Map.entry("ITC", "ITC Ltd"),
            Map.entry("KOTAKBANK", "Kotak Mahindra Bank Ltd"),
            Map.entry("LT", "Larsen & Toubro Ltd"),
            Map.entry("AAPL", "Apple Inc."),
            Map.entry("MSFT", "Microsoft Corporation"),
            Map.entry("AMZN", "Amazon.com Inc."),
            Map.entry("NVDA", "NVIDIA Corporation"),
            Map.entry("GOOGL", "Alphabet Inc."),
            Map.entry("META", "Meta Platforms Inc."),
            Map.entry("TSLA", "Tesla Inc.")
    );

    private CompanyRegistry() {}

    public static String getCompanyName(String symbol) {
        if (symbol == null) {
            return "Unknown Company";
        }
        return COMPANY_NAMES.getOrDefault(symbol.toUpperCase(), symbol + " Corp");
    }
}
