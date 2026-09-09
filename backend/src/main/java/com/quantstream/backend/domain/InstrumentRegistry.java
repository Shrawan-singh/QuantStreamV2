package com.quantstream.backend.domain;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Single source of truth for all supported market instruments across QuantStream.
 *
 * <p>Contains a validated universe of 40 realistic NSE equities (NIFTY 50 components)
 * and 7 US equities for live provider streaming.</p>
 */
public final class InstrumentRegistry {

    private static final Map<String, Instrument> REGISTRY = new LinkedHashMap<>();
    private static final List<String> SIMULATION_SYMBOLS = new ArrayList<>();
    private static final List<String> LIVE_SYMBOLS = new ArrayList<>();
    private static final List<String> CURATED_DASHBOARD_SYMBOLS = List.of(
            "RELIANCE", "TCS", "INFY", "HDFCBANK", "ICICIBANK", "SBIN", "BHARTIARTL", "ITC"
    );

    static {
        // --- 40 Realistic NSE Equities (Indian Market Universe) ---
        register("RELIANCE", "Reliance Industries Ltd", "NSE", "INR", "Energy", "2950.00", true);
        register("TCS", "Tata Consultancy Services Ltd", "NSE", "INR", "Technology", "4200.00", true);
        register("INFY", "Infosys Ltd", "NSE", "INR", "Technology", "1580.00", true);
        register("HDFCBANK", "HDFC Bank Ltd", "NSE", "INR", "Financials", "1720.00", true);
        register("ICICIBANK", "ICICI Bank Ltd", "NSE", "INR", "Financials", "1280.00", true);
        register("SBIN", "State Bank of India", "NSE", "INR", "Financials", "830.00", true);
        register("BHARTIARTL", "Bharti Airtel Ltd", "NSE", "INR", "Telecom", "1620.00", true);
        register("ITC", "ITC Ltd", "NSE", "INR", "FMCG", "495.00", true);
        register("KOTAKBANK", "Kotak Mahindra Bank Ltd", "NSE", "INR", "Financials", "1780.00", true);
        register("LT", "Larsen & Toubro Ltd", "NSE", "INR", "Construction", "3620.00", true);
        register("HINDUNILVR", "Hindustan Unilever Ltd", "NSE", "INR", "FMCG", "2750.00", true);
        register("AXISBANK", "Axis Bank Ltd", "NSE", "INR", "Financials", "1190.00", true);
        register("MARUTI", "Maruti Suzuki India Ltd", "NSE", "INR", "Automobile", "12450.00", true);
        register("SUNPHARMA", "Sun Pharmaceutical Industries Ltd", "NSE", "INR", "Healthcare", "1820.00", true);
        register("TATAMOTORS", "Tata Motors Ltd", "NSE", "INR", "Automobile", "980.00", true);
        register("BAJFINANCE", "Bajaj Finance Ltd", "NSE", "INR", "Financials", "7200.00", true);
        register("ASIANPAINT", "Asian Paints Ltd", "NSE", "INR", "Consumer", "2950.00", true);
        register("TITAN", "Titan Company Ltd", "NSE", "INR", "Consumer", "3550.00", true);
        register("WIPRO", "Wipro Ltd", "NSE", "INR", "Technology", "530.00", true);
        register("ULTRACEMCO", "UltraTech Cement Ltd", "NSE", "INR", "Materials", "11200.00", true);
        register("NTPC", "NTPC Ltd", "NSE", "INR", "Utilities", "410.00", true);
        register("ONGC", "Oil & Natural Gas Corporation Ltd", "NSE", "INR", "Energy", "320.00", true);
        register("POWERGRID", "Power Grid Corporation of India Ltd", "NSE", "INR", "Utilities", "340.00", true);
        register("MM", "Mahindra & Mahindra Ltd", "NSE", "INR", "Automobile", "2780.00", true);
        register("ADANIENT", "Adani Enterprises Ltd", "NSE", "INR", "Diversified", "3050.00", true);
        register("ADANIPORTS", "Adani Ports and Special Economic Zone Ltd", "NSE", "INR", "Infrastructure", "1450.00", true);
        register("COALINDIA", "Coal India Ltd", "NSE", "INR", "Mining", "510.00", true);
        register("TATASTEEL", "Tata Steel Ltd", "NSE", "INR", "Metals", "155.00", true);
        register("JSWSTEEL", "JSW Steel Ltd", "NSE", "INR", "Metals", "940.00", true);
        register("HCLTECH", "HCL Technologies Ltd", "NSE", "INR", "Technology", "1780.00", true);
        register("BAJAJFINSV", "Bajaj Finserv Ltd", "NSE", "INR", "Financials", "1820.00", true);
        register("TECHM", "Tech Mahindra Ltd", "NSE", "INR", "Technology", "1610.00", true);
        register("INDUSINDBK", "IndusInd Bank Ltd", "NSE", "INR", "Financials", "1460.00", true);
        register("NESTLEIND", "Nestle India Ltd", "NSE", "INR", "FMCG", "2520.00", true);
        register("GRASIM", "Grasim Industries Ltd", "NSE", "INR", "Materials", "2650.00", true);
        register("CIPLA", "Cipla Ltd", "NSE", "INR", "Healthcare", "1640.00", true);
        register("DRREDDY", "Dr. Reddy's Laboratories Ltd", "NSE", "INR", "Healthcare", "6600.00", true);
        register("EICHERMOT", "Eicher Motors Ltd", "NSE", "INR", "Automobile", "4900.00", true);
        register("APOLLOHOSP", "Apollo Hospitals Enterprise Ltd", "NSE", "INR", "Healthcare", "6850.00", true);
        register("BPCL", "Bharat Petroleum Corporation Ltd", "NSE", "INR", "Energy", "360.00", true);

        // --- 7 US Equities (Live Market Finnhub Universe) ---
        register("AAPL", "Apple Inc.", "NASDAQ", "USD", "Technology", "225.00", false);
        register("MSFT", "Microsoft Corporation", "NASDAQ", "USD", "Technology", "445.00", false);
        register("AMZN", "Amazon.com Inc.", "NASDAQ", "USD", "Consumer Discretionary", "185.00", false);
        register("NVDA", "NVIDIA Corporation", "NASDAQ", "USD", "Semiconductors", "125.00", false);
        register("GOOGL", "Alphabet Inc.", "NASDAQ", "USD", "Communication", "175.00", false);
        register("META", "Meta Platforms Inc.", "NASDAQ", "USD", "Communication", "515.00", false);
        register("TSLA", "Tesla Inc.", "NASDAQ", "USD", "Automobile", "210.00", false);
    }

    private static void register(String symbol, String name, String exchange, String currency, String sector, String basePrice, boolean isSimulation) {
        Instrument inst = new Instrument(symbol, name, exchange, currency, sector, new BigDecimal(basePrice));
        REGISTRY.put(symbol, inst);
        if (isSimulation) {
            SIMULATION_SYMBOLS.add(symbol);
        } else {
            LIVE_SYMBOLS.add(symbol);
        }
    }

    private InstrumentRegistry() {}

    /**
     * Checks if a symbol exists in the supported universe (case-insensitive, trimmed).
     */
    public static boolean isSupported(String symbol) {
        if (symbol == null || symbol.isBlank()) {
            return false;
        }
        return REGISTRY.containsKey(symbol.trim().toUpperCase());
    }

    /**
     * Checks if a symbol is in the simulation universe.
     */
    public static boolean isSimulationSupported(String symbol) {
        if (symbol == null || symbol.isBlank()) {
            return false;
        }
        return SIMULATION_SYMBOLS.contains(symbol.trim().toUpperCase());
    }

    /**
     * Looks up an instrument by symbol.
     */
    public static Optional<Instrument> getInstrument(String symbol) {
        if (symbol == null || symbol.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(REGISTRY.get(symbol.trim().toUpperCase()));
    }

    /**
     * Returns human-readable company name for a symbol.
     */
    public static String getCompanyName(String symbol) {
        if (symbol == null) {
            return "Unknown Company";
        }
        Instrument inst = REGISTRY.get(symbol.trim().toUpperCase());
        return inst != null ? inst.companyName() : symbol + " Corp";
    }

    /**
     * Returns base reference price for simulation.
     */
    public static BigDecimal getBasePrice(String symbol) {
        if (symbol == null) {
            return new BigDecimal("100.00");
        }
        Instrument inst = REGISTRY.get(symbol.trim().toUpperCase());
        return inst != null ? inst.basePrice() : new BigDecimal("100.00");
    }

    /**
     * Returns all registered instruments.
     */
    public static List<Instrument> getAllSupportedInstruments() {
        return Collections.unmodifiableList(new ArrayList<>(REGISTRY.values()));
    }

    /**
     * Returns all 40 simulation instruments.
     */
    public static List<Instrument> getSimulationUniverse() {
        List<Instrument> list = new ArrayList<>();
        for (String s : SIMULATION_SYMBOLS) {
            list.add(REGISTRY.get(s));
        }
        return Collections.unmodifiableList(list);
    }

    /**
     * Returns all simulation symbol strings.
     */
    public static List<String> getSimulationSymbols() {
        return Collections.unmodifiableList(SIMULATION_SYMBOLS);
    }

    /**
     * Returns curated subset of bellwether instruments for default dashboard view.
     */
    public static List<Instrument> getCuratedDashboardSubset() {
        List<Instrument> list = new ArrayList<>();
        for (String s : CURATED_DASHBOARD_SYMBOLS) {
            Instrument inst = REGISTRY.get(s);
            if (inst != null) {
                list.add(inst);
            }
        }
        return Collections.unmodifiableList(list);
    }

    /**
     * Returns curated dashboard symbol strings.
     */
    public static List<String> getCuratedDashboardSymbols() {
        return Collections.unmodifiableList(CURATED_DASHBOARD_SYMBOLS);
    }
}
