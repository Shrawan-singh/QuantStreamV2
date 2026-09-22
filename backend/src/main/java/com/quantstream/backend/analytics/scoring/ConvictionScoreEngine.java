/*
 * ==================================================================================
 * FILE: ConvictionScoreEngine.java
 * ==================================================================================
 *
 * WHAT THIS FILE DOES:
 * This is the "Brain" of QuantStream! It takes raw indicator results (SMA, EMA, RSI,
 * Momentum, Volume) and combines them into the flagship 0-to-100 "Conviction Score".
 *
 * WHY A SIMPLE AVERAGE IS NOT ENOUGH:
 * Many naive trading systems simply check "is the price 2% above the SMA?".
 * But 2% means completely different things for different stocks:
 *   - For a slow utility stock (like a power company), a 2% move is huge news!
 *   - For a volatile biotech stock or crypto, a 2% move happens every 5 minutes!
 *
 * HOW QUANTSTREAM SOLVES THIS (QUANT FINANCE TECHNIQUES):
 *
 * 1. REALIZED VOLATILITY NORMALIZATION (Z-Scores):
 *    Instead of fixed percentages, we measure the stock's actual heartbeat (volatility \(\sigma\)).
 *    We calculate how many standard deviations (\(Z\)) the price has deviated from normal:
 *      \(Z = \text{Divergence} / \text{Realized Volatility}\)
 *    This ensures fair scoring whether tracking a quiet bank or a fast tech stock!
 *
 * 2. DUAL MOVING AVERAGE CONFIRMATION (EMA + SMA):
 *    - If price is above BOTH SMA and EMA: Strong Bullish (+bonus points).
 *    - If price is below BOTH SMA and EMA: Strong Bearish (-penalty points).
 *    - If SMA says bull but EMA says bear (disagreement): Pull score towards neutral 50.
 *
 * 3. SCORE SMOOTHING (EMA Smoothing):
 *    If raw score jumps from 65 to 68 to 64 every second due to tiny tick noise,
 *    it's distracting. We apply an exponential smoothing factor (\(\alpha = 0.20\)) so
 *    the displayed score glides smoothly like a luxury car's speedometer.
 *
 * 4. CATEGORY HYSTERESIS (Anti-Flicker Buffer):
 *    Imagine the boundary between "NEUTRAL" and "STRONG" is 60.0.
 *    If the score wavers between 59.9 and 60.1, a naive dashboard would flicker
 *    "NEUTRAL" -> "STRONG" -> "NEUTRAL" -> "STRONG" 20 times a minute!
 *    "Hysteresis" adds a buffer (\(\pm 1.5\)): once you enter "STRONG", you don't drop
 *    back to "NEUTRAL" until you fall all the way below 58.5!
 * ==================================================================================
 */

package com.quantstream.backend.analytics.scoring;

import com.quantstream.backend.analytics.indicator.IndicatorResult;
import com.quantstream.backend.analytics.indicator.Signal;
import com.quantstream.backend.analytics.state.MarketState;
import com.quantstream.backend.config.ScoringProperties;
import com.quantstream.backend.domain.InstrumentRegistry;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Deterministic, statistically rigorous Conviction Score engine.
 */
@Component
public class ConvictionScoreEngine {

    private final ScoringProperties properties;

    // Cache remembering the previous score and category for each symbol (needed for smoothing & hysteresis)
    private final ConcurrentHashMap<String, SymbolScoreState> symbolStates = new ConcurrentHashMap<>();

    public ConvictionScoreEngine(ScoringProperties properties) {
        this.properties = properties != null ? properties : ScoringProperties.defaultProperties();
    }

    /**
     * Evaluates conviction score given market state snapshot and computed indicator results.
     */
    public ConvictionScore evaluate(
            MarketState.Snapshot snapshot,
            IndicatorResult smaResult,
            IndicatorResult emaResult,
            IndicatorResult rsiResult,
            IndicatorResult momentumResult,
            IndicatorResult rvolResult
    ) {
        String symbol = snapshot != null ? snapshot.symbol() : "UNKNOWN";
        Instant timestamp = snapshot != null && snapshot.latestTimestamp() != null
                ? snapshot.latestTimestamp()
                : Instant.now();

        // Check if ANY indicator is ready yet. If none are ready, return warm-up state.
        boolean anyReady = (smaResult != null && smaResult.ready()) ||
                (emaResult != null && emaResult.ready()) ||
                (rsiResult != null && rsiResult.ready()) ||
                (momentumResult != null && momentumResult.ready()) ||
                (rvolResult != null && rvolResult.ready());

        if (!anyReady) {
            return ConvictionScore.notReady(symbol, timestamp);
        }

        // Normalize weights so they always sum up to 100% (e.g. 25% + 25% + 25% + 25% = 1.0)
        double totalWeight = properties.totalWeight();
        if (totalWeight <= 0.0) {
            totalWeight = 100.0;
        }

        double wTrend = properties.trendWeight() / totalWeight;
        double wMomentum = properties.momentumWeight() / totalWeight;
        double wRsi = properties.rsiWeight() / totalWeight;
        double wVolume = properties.volumeWeight() / totalWeight;

        // Step 0: Compute Realized Volatility from price history (rolling standard deviation of log returns)
        List<BigDecimal> recentPrices = snapshot != null ? snapshot.recentPrices() : List.of();
        double realizedVol = calculateRealizedVolatility(
                recentPrices,
                properties.volatilityLookback(),
                properties.minVolatilityLookback()
        );

        // Determine currency symbol ($ for US stocks, ₹ for Indian stocks) for nice user explanations
        String curSym = InstrumentRegistry.getInstrument(symbol)
                .map(com.quantstream.backend.domain.Instrument::currency)
                .filter("USD"::equalsIgnoreCase)
                .map(c -> "$")
                .orElse("₹");

        List<String> explanations = new ArrayList<>(6);

        // =========================================================================
        // FACTOR 1: TREND SCORE [0 to 100] (Price vs Moving Averages)
        // =========================================================================
        double trendScore = 50.0;
        Signal trendSignal = Signal.NOT_READY;
        boolean hasSma = smaResult != null && smaResult.ready();
        boolean hasEma = emaResult != null && emaResult.ready();

        if ((hasSma || hasEma) && snapshot != null && snapshot.latestPrice() != null) {
            double currentPrice = snapshot.latestPrice().doubleValue();
            double benchmarkVal = hasSma ? smaResult.value() : emaResult.value();
            String benchmarkName = hasSma ? "SMA" : "EMA";

            if (benchmarkVal > 0.0) {
                // Percentage divergence from benchmark average: (Price - Benchmark) / Benchmark
                double smaDivergence = (currentPrice - benchmarkVal) / benchmarkVal;

                if (realizedVol < 0.0) {
                    // Not enough history to calculate volatility yet -> stay neutral 50.0
                    trendScore = 50.0;
                    trendSignal = Signal.NOT_READY;
                    explanations.add(String.format("Trend: 50.0 (Price %s%.2f vs %s %s%.2f; awaiting %d-period return history for realized volatility)",
                            curSym, currentPrice, benchmarkName, curSym, benchmarkVal, properties.minVolatilityLookback()));
                } else {
                    double effectiveVol = realizedVol;
                    boolean usedVolFloor = false;
                    // If volatility is zero (stock didn't move at all), apply a safe floor so we don't divide by zero
                    if (effectiveVol < 1e-6) {
                        if (Math.abs(smaDivergence) < 1e-6) {
                            effectiveVol = 0.0;
                        } else {
                            effectiveVol = 0.005; // 0.5% return volatility floor fallback
                            usedVolFloor = true;
                        }
                    }

                    // Z-score: how many standard deviations is the price above/below the benchmark?
                    double trendZ = 0.0;
                    if (effectiveVol > 0.0) {
                        trendZ = smaDivergence / effectiveVol;
                    }

                    // Map Z-score to 0-100 scale (Z=0 maps to 50, Z=+3 maps to 100, Z=-3 maps to 0)
                    double trendBase = clamp(50.0 + (trendZ / properties.zScoreCap()) * 50.0, 0.0, 100.0);

                    // Dual Moving Average Confirmation (SMA + EMA together)
                    String emaExplanation = null;
                    if (hasSma && hasEma) {
                        double smaVal = smaResult.value();
                        double emaVal = emaResult.value();

                        if (currentPrice > smaVal && currentPrice > emaVal) {
                            // Both averages agree bullish! Give bonus points.
                            trendScore = clamp(trendBase + properties.emaConfirmationBonus(), 0.0, 100.0);
                            emaExplanation = String.format("SMA and EMA agree bullish (+%.1f pts trend confirmation)", properties.emaConfirmationBonus());
                        } else if (currentPrice < smaVal && currentPrice < emaVal) {
                            // Both averages agree bearish! Apply penalty.
                            trendScore = clamp(trendBase - properties.emaConfirmationBonus(), 0.0, 100.0);
                            emaExplanation = String.format("SMA and EMA agree bearish (-%.1f pts negative trend confirmation)", properties.emaConfirmationBonus());
                        } else {
                            // Divergence (one is above, one is below): pull score back towards neutral 50.0
                            double penalty = properties.emaDivergencePenalty();
                            if (trendBase > 50.0) {
                                trendScore = Math.max(50.0, trendBase - penalty);
                            } else if (trendBase < 50.0) {
                                trendScore = Math.min(50.0, trendBase + penalty);
                            } else {
                                trendScore = 50.0;
                            }
                            emaExplanation = String.format("SMA and EMA diverge, reducing trend confirmation (%.1f pts penalty toward 50.0)", penalty);
                        }
                    } else {
                        trendScore = trendBase;
                        emaExplanation = hasSma
                                ? "EMA unavailable; relying solely on SMA divergence without dual-MA confirmation"
                                : "SMA unavailable; using EMA as trend benchmark without dual-MA confirmation";
                    }

                    trendSignal = scoreToSignal(trendScore);

                    String volNote = usedVolFloor
                            ? "zero volatility floor fallback: 0.50%"
                            : String.format("realized vol: %.2f%%", realizedVol * 100.0);
                    explanations.add(String.format("Trend: %.1f (Price %s%.2f vs %s %s%.2f, div: %+.2f%%, %s, Z: %+.2fσ)",
                            trendScore, curSym, currentPrice, benchmarkName, curSym, benchmarkVal, smaDivergence * 100.0, volNote, trendZ));
                    explanations.add("EMA confirmation: " + emaExplanation);
                }
            } else {
                explanations.add("Trend: 50.0 (Benchmark value <= 0)");
            }
        } else {
            explanations.add("Trend: 50.0 (Awaiting sufficient history for SMA/EMA)");
        }

        // =========================================================================
        // FACTOR 2: MOMENTUM SCORE [0 to 100] (Rate of Change vs Volatility)
        // =========================================================================
        double momentumScore = 50.0;
        Signal momentumSignal = Signal.NOT_READY;
        if (momentumResult != null && momentumResult.ready()) {
            double momentumPct = momentumResult.value();
            double momentumReturn = momentumPct / 100.0;

            if (realizedVol < 0.0) {
                momentumScore = 50.0;
                momentumSignal = Signal.NOT_READY;
                explanations.add(String.format("Momentum: 50.0 (Lookback change: %+.2f%%; awaiting realized volatility history)", momentumPct));
            } else {
                double effectiveVol = realizedVol;
                boolean usedVolFloor = false;
                if (effectiveVol < 1e-6) {
                    if (Math.abs(momentumReturn) < 1e-6) {
                        effectiveVol = 0.0;
                    } else {
                        effectiveVol = 0.005;
                        usedVolFloor = true;
                    }
                }

                double momentumZ = 0.0;
                if (effectiveVol > 0.0) {
                    momentumZ = momentumReturn / effectiveVol;
                }

                // Map Momentum Z-score to 0-100 scale
                momentumScore = clamp(50.0 + (momentumZ / properties.zScoreCap()) * 50.0, 0.0, 100.0);
                momentumSignal = scoreToSignal(momentumScore);

                String volNote = usedVolFloor
                        ? "vol floor 0.50%"
                        : String.format("realized vol: %.2f%%", realizedVol * 100.0);
                explanations.add(String.format("Momentum: %.1f (Lookback change: %+.2f%%, %s, Z: %+.2fσ)",
                        momentumScore, momentumPct, volNote, momentumZ));
            }
        } else {
            explanations.add("Momentum: 50.0 (Awaiting lookback period)");
        }

        // =========================================================================
        // FACTOR 3: RSI SCORE [0 to 100]
        // =========================================================================
        double rsiScore = 50.0;
        Signal rsiSignal = Signal.NOT_READY;
        if (rsiResult != null && rsiResult.ready()) {
            rsiScore = clamp(rsiResult.value(), 0.0, 100.0);
            rsiSignal = scoreToSignal(rsiScore);
            explanations.add(String.format("RSI: %.1f (14-period index: %.1f)", rsiScore, rsiResult.value()));
        } else {
            explanations.add("RSI: 50.0 (Awaiting 14-period warm-up)");
        }

        // =========================================================================
        // FACTOR 4: RELATIVE VOLUME SCORE [0 to 100]
        // =========================================================================
        double volumeScore = 50.0;
        Signal volumeSignal = Signal.NOT_READY;
        if (rvolResult != null && rvolResult.ready()) {
            double rvol = rvolResult.value();
            // Baseline 1.0x RVOL maps directly to 50.0 score (2.0x maps to 100.0)
            volumeScore = clamp(rvol * 50.0, 0.0, 100.0);
            volumeSignal = scoreToSignal(volumeScore);
            explanations.add(String.format("Volume: %.1f (%.2fx of 20-period baseline)", volumeScore, rvolResult.value()));
        } else {
            explanations.add("Volume: 50.0 (Awaiting volume history)");
        }

        // Round factor scores to 1 decimal place
        trendScore = roundOneDecimal(trendScore);
        momentumScore = roundOneDecimal(momentumScore);
        rsiScore = roundOneDecimal(rsiScore);
        volumeScore = roundOneDecimal(volumeScore);

        // Compute each factor's weighted contribution (points added to total)
        double trendContribution = roundOneDecimal(wTrend * trendScore);
        double momentumContribution = roundOneDecimal(wMomentum * momentumScore);
        double rsiContribution = roundOneDecimal(wRsi * rsiScore);
        double volumeContribution = roundOneDecimal(wVolume * volumeScore);

        // Raw Composite Score = sum of weighted contributions
        double rawFinalScore = trendContribution + momentumContribution + rsiContribution + volumeContribution;
        double rawScore = roundOneDecimal(clamp(rawFinalScore, 0.0, 100.0));

        // =========================================================================
        // STEP 5: SCORE SMOOTHING (EMA) & CATEGORY HYSTERESIS
        // =========================================================================
        SymbolScoreState prevState = symbolStates.get(symbol);
        double alpha = properties.smoothingAlpha(); // default: 0.20 (20% new, 80% previous)
        double smoothed;

        if (prevState == null) {
            smoothed = rawScore; // First score ever for this stock
        } else {
            smoothed = (alpha * rawScore) + ((1.0 - alpha) * prevState.smoothedScore);
        }
        double smoothedScore = roundOneDecimal(clamp(smoothed, 0.0, 100.0));

        // Use hysteresis to decide the category (prevents flickering around 40 and 60)
        ScoreCategory lastCategory = prevState != null ? prevState.lastCategory : null;
        ScoreCategory category = calculateCategoryWithHysteresis(smoothedScore, lastCategory, properties.hysteresisBand());

        // Save new state in cache for next time
        symbolStates.put(symbol, new SymbolScoreState(smoothedScore, category, rawScore));

        explanations.add(String.format("Score smoothing & hysteresis: displayed=%.1f (raw=%.1f, α=%.2f; category=%s with ±%.1f hysteresis)",
                smoothedScore, rawScore, alpha, category.getDisplayName(), properties.hysteresisBand()));

        Map<String, Signal> signals = new HashMap<>();
        signals.put("trend", trendSignal);
        signals.put("momentum", momentumSignal);
        signals.put("rsi", rsiSignal);
        signals.put("volume", volumeSignal);

        double displayVolatility = realizedVol >= 0.0 ? roundFourDecimals(realizedVol) : 0.0;

        return new ConvictionScore(
                symbol,
                smoothedScore,
                category,
                trendScore,
                momentumScore,
                rsiScore,
                volumeScore,
                trendContribution,
                momentumContribution,
                rsiContribution,
                volumeContribution,
                explanations,
                signals,
                true,
                timestamp,
                rawScore,
                smoothedScore,
                displayVolatility
        );
    }

    /**
     * Calculates rolling standard deviation of log returns: r_t = ln(P_t / P_{t-1}).
     *
     * @param prices recent price series
     * @param lookback maximum lookback window
     * @param minLookback minimum prices needed to compute volatility
     * @return sample standard deviation of log returns, or -1.0 if insufficient history
     */
    public static double calculateRealizedVolatility(List<BigDecimal> prices, int lookback, int minLookback) {
        if (prices == null || prices.size() < minLookback || prices.size() < 2) {
            return -1.0;
        }

        int size = prices.size();
        int window = Math.min(size, lookback);
        int startIndex = size - window;

        int returnsCount = window - 1;
        if (returnsCount < 1) {
            return -1.0;
        }

        double[] logReturns = new double[returnsCount];
        double sum = 0.0;

        for (int i = 0; i < returnsCount; i++) {
            double pPrev = prices.get(startIndex + i).doubleValue();
            double pCurr = prices.get(startIndex + i + 1).doubleValue();

            if (pPrev <= 0.0 || pCurr <= 0.0) {
                return -1.0;
            }

            // Natural log return: ln(Current / Previous)
            double r = Math.log(pCurr / pPrev);
            logReturns[i] = r;
            sum += r;
        }

        double mean = sum / returnsCount;

        if (returnsCount == 1) {
            return Math.abs(logReturns[0]);
        }

        // Sum of squared differences from the average
        double sumSquaredDiff = 0.0;
        for (double r : logReturns) {
            double diff = r - mean;
            sumSquaredDiff += diff * diff;
        }

        // Sample variance and standard deviation
        double variance = sumSquaredDiff / (returnsCount - 1);
        double stdDev = Math.sqrt(variance);

        return Double.isNaN(stdDev) ? 0.0 : stdDev;
    }

    /**
     * Prevents flickering between categories (e.g. between NEUTRAL and STRONG) by
     * requiring the score to cross a hysteresis buffer band 'h' (e.g. 1.5 points).
     */
    public static ScoreCategory calculateCategoryWithHysteresis(double score, ScoreCategory previous, double h) {
        if (previous == null) {
            return ScoreCategory.fromScore(score);
        }

        switch (previous) {
            case VERY_STRONG:
                // Normal boundary is 80.0. To fall to STRONG, must drop below (80.0 - h)
                if (score < (80.0 - h)) {
                    return calculateCategoryWithHysteresis(score, ScoreCategory.STRONG, h);
                }
                return ScoreCategory.VERY_STRONG;

            case STRONG:
                // To rise to VERY_STRONG: score >= 80.0 + h
                if (score >= (80.0 + h)) {
                    return ScoreCategory.VERY_STRONG;
                }
                // Normal boundary is 60.0. To fall to NEUTRAL, must drop below (60.0 - h) (e.g. < 58.5)
                if (score < (60.0 - h)) {
                    return calculateCategoryWithHysteresis(score, ScoreCategory.NEUTRAL, h);
                }
                return ScoreCategory.STRONG;

            case NEUTRAL:
                // Normal boundary is 60.0. To rise to STRONG, must exceed (60.0 + h) (e.g. >= 61.5)
                if (score >= (60.0 + h)) {
                    return ScoreCategory.STRONG;
                }
                // Normal boundary is 40.0. To fall to WEAK, must drop below (40.0 - h) (e.g. <= 38.5)
                if (score <= (40.0 - h)) {
                    return ScoreCategory.WEAK;
                }
                return ScoreCategory.NEUTRAL;

            case WEAK:
                // Normal boundary is 40.0. To rise to NEUTRAL, must exceed (40.0 + h) (e.g. > 41.5)
                if (score > (40.0 + h)) {
                    return calculateCategoryWithHysteresis(score, ScoreCategory.NEUTRAL, h);
                }
                // Normal boundary is 20.0. To fall to VERY_WEAK, must drop below (20.0 - h)
                if (score <= (20.0 - h)) {
                    return ScoreCategory.VERY_WEAK;
                }
                return ScoreCategory.WEAK;

            case VERY_WEAK:
                // To rise to WEAK: score > 20.0 + h
                if (score > (20.0 + h)) {
                    return calculateCategoryWithHysteresis(score, ScoreCategory.WEAK, h);
                }
                return ScoreCategory.VERY_WEAK;

            default:
                return ScoreCategory.fromScore(score);
        }
    }

    private Signal scoreToSignal(double score) {
        if (score >= 60.0) return Signal.POSITIVE;
        if (score <= 40.0) return Signal.NEGATIVE;
        return Signal.NEUTRAL;
    }

    private static double clamp(double val, double min, double max) {
        return Math.max(min, Math.min(max, val));
    }

    private static double roundOneDecimal(double val) {
        return BigDecimal.valueOf(val).setScale(1, RoundingMode.HALF_UP).doubleValue();
    }

    private static double roundFourDecimals(double val) {
        return BigDecimal.valueOf(val).setScale(4, RoundingMode.HALF_UP).doubleValue();
    }

    public void clear() {
        symbolStates.clear();
    }

    public void resetSymbol(String symbol) {
        if (symbol != null) {
            symbolStates.remove(symbol.trim().toUpperCase());
        }
    }

    // Helper holder class to remember previous state of each symbol
    public static final class SymbolScoreState {
        final double smoothedScore;
        final ScoreCategory lastCategory;
        final double rawScore;

        public SymbolScoreState(double smoothedScore, ScoreCategory lastCategory, double rawScore) {
            this.smoothedScore = smoothedScore;
            this.lastCategory = lastCategory;
            this.rawScore = rawScore;
        }

        public double getSmoothedScore() {
            return smoothedScore;
        }

        public ScoreCategory getCategory() {
            return lastCategory;
        }

        public double getRawScore() {
            return rawScore;
        }
    }
}
