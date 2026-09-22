/*
 * ==================================================================================
 * FILE: InstrumentRegistry.java
 * ==================================================================================
 *
 * WHAT THIS FILE DOES:
 * Think of this as the "Phonebook" or "Master Directory" for all stocks that
 * QuantStream knows about. If a stock isn't in this registry, QuantStream won't
 * track it or process its prices.
 *
 * TWO DIFFERENT UNIVERSES OF STOCKS:
 * QuantStream operates in two distinct modes, and each has its own list ("universe")
 * of stocks:
 *
 * 1. SIMULATION MODE (NSE Indian Stocks):
 *    - ~240 Indian equities (like RELIANCE, TCS, INFY, HDFCBANK).
 *    - Loaded automatically from a spreadsheet/text file: "instruments-simulation.csv".
 *    - The backend generates realistic synthetic prices for these using math formulas.
 *
 * 2. LIVE MODE (US Equities via Finnhub API):
 *    - 50 major US stocks (like AAPL, MSFT, NVDA, AMZN, TSLA, GOOGL).
 *    - Registered directly in code below with real company names, sectors, and baseline prices.
 *    - Connected to Finnhub's WebSocket to receive REAL live trade prices from the US stock market!
 *
 * WHY A "REGISTRY" IS NEEDED:
 * Instead of hardcoding stock names all over the codebase, any service (analytics,
 * alerts, websocket, web UI) asks this registry:
 *   - "Is this symbol valid?" -> isSupported("AAPL")
 *   - "What is the full company name?" -> getCompanyName("AAPL") -> "Apple Inc."
 *   - "What stocks should we stream right now?" -> getActiveUniverse("live")
 * ==================================================================================
 */

package com.quantstream.backend.domain;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Single source of truth for all supported market instruments across QuantStream.
 *
 * <p>Loads a validated universe of ~240 NSE equities from classpath resource:
 * {@code instruments-simulation.csv} and maintains a distinct 50 US equity universe
 * for live market streaming.</p>
 */
public final class InstrumentRegistry {

    private static final Logger logger = LoggerFactory.getLogger(InstrumentRegistry.class);

    // Path to the CSV file inside the jar/resources folder containing Indian simulation stocks
    private static final String SIMULATION_CSV_PATH = "instruments-simulation.csv";

    // Master map: Symbol string (e.g. "AAPL") -> Instrument record
    // LinkedHashMap preserves insertion order so stocks display in a predictable sequence
    private static final Map<String, Instrument> REGISTRY = new LinkedHashMap<>();

    // List of just the simulation symbols (e.g. ["RELIANCE", "TCS", ...])
    private static final List<String> SIMULATION_SYMBOLS = new ArrayList<>();

    // List of just the live US symbols (e.g. ["AAPL", "MSFT", ...])
    private static final List<String> LIVE_SYMBOLS = new ArrayList<>();

    // A small hand-picked subset of famous stocks shown prominently on the dashboard
    private static final List<String> CURATED_DASHBOARD_SYMBOLS = List.of(
            "RELIANCE", "TCS", "INFY", "HDFCBANK", "ICICIBANK", "SBIN", "BHARTIARTL", "ITC"
    );

    /*
     * STATIC INITIALIZER BLOCK:
     * In Java, code inside "static { ... }" runs ONCE when the application first starts up,
     * before any requests or background jobs run.
     * Here we populate both stock lists so they are ready in memory.
     */
    static {
        // 1. Load simulation instruments from external CSV resource
        loadSimulationInstrumentsFromCsv();

        // 2. Register 50 liquid US Equities across diverse sectors (Live Market Finnhub Universe)
        // Mega-Cap Tech & Semiconductors
        registerLive("AAPL", "Apple Inc.", "NASDAQ", "USD", "Technology", "235.00");
        registerLive("MSFT", "Microsoft Corporation", "NASDAQ", "USD", "Technology", "450.00");
        registerLive("NVDA", "NVIDIA Corporation", "NASDAQ", "USD", "Semiconductors", "130.00");
        registerLive("AVGO", "Broadcom Inc.", "NASDAQ", "USD", "Semiconductors", "175.00");
        registerLive("AMD", "Advanced Micro Devices Inc.", "NASDAQ", "USD", "Semiconductors", "155.00");
        registerLive("QCOM", "QUALCOMM Incorporated", "NASDAQ", "USD", "Semiconductors", "170.00");
        registerLive("INTC", "Intel Corporation", "NASDAQ", "USD", "Semiconductors", "22.00");
        registerLive("CSCO", "Cisco Systems Inc.", "NASDAQ", "USD", "Technology", "50.00");
        registerLive("ORCL", "Oracle Corporation", "NYSE", "USD", "Technology", "140.00");
        registerLive("CRM", "Salesforce Inc.", "NYSE", "USD", "Technology", "260.00");
        registerLive("IBM", "International Business Machines", "NYSE", "USD", "Technology", "200.00");
        registerLive("ADBE", "Adobe Inc.", "NASDAQ", "USD", "Technology", "550.00");
        registerLive("UBER", "Uber Technologies Inc.", "NYSE", "USD", "Technology", "75.00");

        // Consumer Discretionary & Auto
        registerLive("AMZN", "Amazon.com Inc.", "NASDAQ", "USD", "Consumer Discretionary", "190.00");
        registerLive("TSLA", "Tesla Inc.", "NASDAQ", "USD", "Automobile", "220.00");
        registerLive("HD", "The Home Depot Inc.", "NYSE", "USD", "Consumer Discretionary", "380.00");
        registerLive("MCD", "McDonald's Corporation", "NYSE", "USD", "Consumer Discretionary", "290.00");
        registerLive("NKE", "NIKE Inc.", "NYSE", "USD", "Consumer Discretionary", "85.00");
        registerLive("SBUX", "Starbucks Corporation", "NASDAQ", "USD", "Consumer Discretionary", "95.00");

        // Communication Services & Media
        registerLive("GOOGL", "Alphabet Inc.", "NASDAQ", "USD", "Communication", "180.00");
        registerLive("META", "Meta Platforms Inc.", "NASDAQ", "USD", "Communication", "520.00");
        registerLive("NFLX", "Netflix Inc.", "NASDAQ", "USD", "Communication", "690.00");
        registerLive("DIS", "The Walt Disney Company", "NYSE", "USD", "Communication", "95.00");
        registerLive("CMCSA", "Comcast Corporation", "NASDAQ", "USD", "Communication", "40.00");

        // Financials & Banking
        registerLive("JPM", "JPMorgan Chase & Co.", "NYSE", "USD", "Financial Services", "215.00");
        registerLive("V", "Visa Inc.", "NYSE", "USD", "Financial Services", "280.00");
        registerLive("MA", "Mastercard Incorporated", "NYSE", "USD", "Financial Services", "480.00");
        registerLive("BAC", "Bank of America Corporation", "NYSE", "USD", "Financial Services", "40.00");
        registerLive("WFC", "Wells Fargo & Company", "NYSE", "USD", "Financial Services", "55.00");
        registerLive("MS", "Morgan Stanley", "NYSE", "USD", "Financial Services", "100.00");
        registerLive("GS", "Goldman Sachs Group Inc.", "NYSE", "USD", "Financial Services", "480.00");

        // Healthcare & Pharma
        registerLive("UNH", "UnitedHealth Group Inc.", "NYSE", "USD", "Healthcare", "580.00");
        registerLive("JNJ", "Johnson & Johnson", "NYSE", "USD", "Healthcare", "160.00");
        registerLive("LLY", "Eli Lilly and Company", "NYSE", "USD", "Healthcare", "950.00");
        registerLive("ABBV", "AbbVie Inc.", "NYSE", "USD", "Healthcare", "195.00");
        registerLive("MRK", "Merck & Co. Inc.", "NYSE", "USD", "Healthcare", "120.00");
        registerLive("TMO", "Thermo Fisher Scientific Inc.", "NYSE", "USD", "Healthcare", "600.00");
        registerLive("PFE", "Pfizer Inc.", "NYSE", "USD", "Healthcare", "30.00");
        registerLive("ABT", "Abbott Laboratories", "NYSE", "USD", "Healthcare", "115.00");

        // Consumer Staples (Retail & Everyday Goods)
        registerLive("WMT", "Walmart Inc.", "NYSE", "USD", "Consumer Staples", "70.00");
        registerLive("PG", "Procter & Gamble Company", "NYSE", "USD", "Consumer Staples", "175.00");
        registerLive("COST", "Costco Wholesale Corporation", "NASDAQ", "USD", "Consumer Staples", "880.00");
        registerLive("KO", "The Coca-Cola Company", "NYSE", "USD", "Consumer Staples", "70.00");
        registerLive("PEP", "PepsiCo Inc.", "NASDAQ", "USD", "Consumer Staples", "175.00");

        // Energy (Oil & Gas)
        registerLive("XOM", "Exxon Mobil Corporation", "NYSE", "USD", "Energy", "115.00");
        registerLive("CVX", "Chevron Corporation", "NYSE", "USD", "Energy", "150.00");

        // Industrials & Aerospace
        registerLive("CAT", "Caterpillar Inc.", "NYSE", "USD", "Industrials", "350.00");
        registerLive("BA", "The Boeing Company", "NYSE", "USD", "Industrials", "160.00");
        registerLive("HON", "Honeywell International Inc.", "NASDAQ", "USD", "Industrials", "205.00");
        registerLive("GE", "GE Aerospace", "NYSE", "USD", "Industrials", "180.00");

        logger.info("InstrumentRegistry initialized: {} simulation symbols (NSE), {} live symbols (US)",
                SIMULATION_SYMBOLS.size(), LIVE_SYMBOLS.size());
    }

    /**
     * Reads the simulation CSV file line by line, validates each column, and registers each stock.
     */
    private static void loadSimulationInstrumentsFromCsv() {
        InputStream is = InstrumentRegistry.class.getClassLoader().getResourceAsStream(SIMULATION_CSV_PATH);
        if (is == null) {
            throw new IllegalStateException("Failed to locate required classpath resource: " + SIMULATION_CSV_PATH);
        }

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            String line;
            int lineNumber = 0;
            boolean headerFound = false;

            while ((line = reader.readLine()) != null) {
                lineNumber++;
                String trimmed = line.trim();
                // Skip empty lines and comment lines that start with '#'
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue;
                }

                String[] parts = parseCsvLine(trimmed);
                // First non-comment line must be the header (symbol, companyName, etc.)
                if (!headerFound) {
                    if (parts.length >= 6 && parts[0].equalsIgnoreCase("symbol")) {
                        headerFound = true;
                        continue;
                    } else {
                        throw new IllegalStateException("Invalid CSV header in " + SIMULATION_CSV_PATH + " at line " + lineNumber);
                    }
                }

                // Check that line has all 6 required fields
                if (parts.length < 6) {
                    throw new IllegalStateException("Malformed row in " + SIMULATION_CSV_PATH + " at line " + lineNumber + ": expected 6 columns, found " + parts.length);
                }

                String symbol = parts[0].trim().toUpperCase();
                String companyName = parts[1].trim();
                String exchange = parts[2].trim();
                String currency = parts[3].trim();
                String sector = parts[4].trim();
                String basePriceStr = parts[5].trim();

                if (symbol.isEmpty()) {
                    throw new IllegalStateException("Empty symbol in " + SIMULATION_CSV_PATH + " at line " + lineNumber);
                }

                // Guard against duplicate tickers in the CSV
                if (REGISTRY.containsKey(symbol)) {
                    throw new IllegalStateException("Duplicate symbol detected in InstrumentRegistry: " + symbol + " at line " + lineNumber);
                }

                BigDecimal basePrice;
                try {
                    basePrice = new BigDecimal(basePriceStr);
                } catch (NumberFormatException e) {
                    throw new IllegalStateException("Invalid base price '" + basePriceStr + "' for symbol " + symbol + " at line " + lineNumber, e);
                }

                // Create the Instrument object and store it in both the master map and the simulation list
                Instrument instrument = new Instrument(symbol, companyName, exchange, currency, sector, basePrice);
                REGISTRY.put(symbol, instrument);
                SIMULATION_SYMBOLS.add(symbol);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Error reading " + SIMULATION_CSV_PATH, e);
        }
    }

    /**
     * Helper to split a CSV line by comma, while properly handling quotes (e.g., "Apple, Inc.").
     */
    private static String[] parseCsvLine(String line) {
        List<String> tokens = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '\"') {
                inQuotes = !inQuotes;
            } else if (c == ',' && !inQuotes) {
                tokens.add(sb.toString().trim());
                sb.setLength(0);
            } else {
                sb.append(c);
            }
        }
        tokens.add(sb.toString().trim());
        return tokens.toArray(new String[0]);
    }

    /**
     * Helper to add a US stock to the live universe.
     * Prevents adding duplicate symbols and converts basePrice string to BigDecimal.
     */
    private static void registerLive(String symbol, String name, String exchange, String currency, String sector, String basePrice) {
        String sym = symbol.trim().toUpperCase();
        if (REGISTRY.containsKey(sym)) {
            throw new IllegalStateException("Duplicate symbol detected: " + sym + " conflicts between live and simulation universes");
        }
        Instrument inst = new Instrument(sym, name, exchange, currency, sector, new BigDecimal(basePrice));
        REGISTRY.put(sym, inst);
        LIVE_SYMBOLS.add(sym);
    }

    /**
     * Checks if a symbol is supported in the currently active market mode.
     *
     * @param symbol the ticker symbol to validate (e.g. "AAPL" or "RELIANCE")
     * @param mode   the active market mode ("simulation" or "live")
     * @return true if the symbol belongs to the given mode's universe
     */
    public static boolean isSupportedInCurrentMode(String symbol, String mode) {
        if (symbol == null || symbol.isBlank() || mode == null) {
            return false;
        }
        String normalizedMode = mode.trim().toLowerCase();
        return switch (normalizedMode) {
            case "simulation" -> isSimulationSupported(symbol);
            case "live" -> isLiveSupported(symbol);
            default -> isSupported(symbol);
        };
    }

    /**
     * Returns only the instruments belonging to the active market mode.
     *
     * @param mode the active market mode ("simulation" or "live")
     * @return list of instruments for the active universe
     */
    public static List<Instrument> getActiveUniverse(String mode) {
        if (mode == null) {
            return getAllSupportedInstruments();
        }
        String normalizedMode = mode.trim().toLowerCase();
        return switch (normalizedMode) {
            case "simulation" -> getSimulationUniverse();
            case "live" -> getLiveUniverse();
            default -> getAllSupportedInstruments();
        };
    }

    // Private constructor prevents anyone from instantiating this utility class with "new InstrumentRegistry()"
    private InstrumentRegistry() {}

    /**
     * Checks if a symbol exists anywhere in QuantStream (simulation OR live).
     * Case-insensitive: "aapl", "AAPL", "  aapl  " all match correctly.
     */
    public static boolean isSupported(String symbol) {
        if (symbol == null || symbol.isBlank()) {
            return false;
        }
        return REGISTRY.containsKey(symbol.trim().toUpperCase());
    }

    /**
     * Checks if a symbol is in the simulation (Indian NSE) universe.
     */
    public static boolean isSimulationSupported(String symbol) {
        if (symbol == null || symbol.isBlank()) {
            return false;
        }
        return SIMULATION_SYMBOLS.contains(symbol.trim().toUpperCase());
    }

    /**
     * Checks if a symbol is in the live US market (Finnhub) universe.
     */
    public static boolean isLiveSupported(String symbol) {
        if (symbol == null || symbol.isBlank()) {
            return false;
        }
        return LIVE_SYMBOLS.contains(symbol.trim().toUpperCase());
    }

    /**
     * Looks up an instrument by its symbol.
     * Returns an Optional, which is empty if the symbol is not found.
     */
    public static Optional<Instrument> getInstrument(String symbol) {
        if (symbol == null || symbol.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(REGISTRY.get(symbol.trim().toUpperCase()));
    }

    /**
     * Returns human-readable company name for a symbol (e.g. "AAPL" -> "Apple Inc.").
     * Falls back to "Unknown Company" or "SYMBOL Corp" if not found.
     */
    public static String getCompanyName(String symbol) {
        if (symbol == null) {
            return "Unknown Company";
        }
        Instrument inst = REGISTRY.get(symbol.trim().toUpperCase());
        return inst != null ? inst.companyName() : symbol + " Corp";
    }

    /**
     * Returns base reference price for simulation initialization.
     * Note: basePrice is ONLY a deterministic seed, never a live market quote.
     */
    public static BigDecimal getBasePrice(String symbol) {
        if (symbol == null) {
            return new BigDecimal("100.00");
        }
        Instrument inst = REGISTRY.get(symbol.trim().toUpperCase());
        return inst != null ? inst.basePrice() : new BigDecimal("100.00");
    }

    /**
     * Returns all registered instruments across all universes (simulation + live).
     */
    public static List<Instrument> getAllSupportedInstruments() {
        return Collections.unmodifiableList(new ArrayList<>(REGISTRY.values()));
    }

    /**
     * Returns all simulation instruments (~240 validated NSE equities).
     */
    public static List<Instrument> getSimulationUniverse() {
        List<Instrument> list = new ArrayList<>();
        for (String s : SIMULATION_SYMBOLS) {
            list.add(REGISTRY.get(s));
        }
        return Collections.unmodifiableList(list);
    }

    /**
     * Returns all simulation symbol strings as a list.
     */
    public static List<String> getSimulationSymbols() {
        return Collections.unmodifiableList(SIMULATION_SYMBOLS);
    }

    /**
     * Returns all live US instruments (50 Finnhub equities).
     */
    public static List<Instrument> getLiveUniverse() {
        List<Instrument> list = new ArrayList<>();
        for (String s : LIVE_SYMBOLS) {
            list.add(REGISTRY.get(s));
        }
        return Collections.unmodifiableList(list);
    }

    /**
     * Returns all live symbol strings as a list.
     */
    public static List<String> getLiveSymbols() {
        return Collections.unmodifiableList(LIVE_SYMBOLS);
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
