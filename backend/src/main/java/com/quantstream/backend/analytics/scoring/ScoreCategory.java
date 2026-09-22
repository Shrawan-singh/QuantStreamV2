/*
 * ==================================================================================
 * FILE: ScoreCategory.java
 * ==================================================================================
 *
 * WHAT THIS FILE DOES:
 * Converts the numerical Conviction Score (0.0 to 100.0) into a plain-English label
 * that human traders and dashboard users can understand at a glance.
 *
 * THE 5 RATING TIERS:
 *   - VERY_WEAK   (0 to 20):   Heavy downward/bearish pressure across all indicators.
 *   - WEAK        (21 to 40):  Leaning bearish / negative trend.
 *   - NEUTRAL     (41 to 60):  Indecisive / Sideways chop / Indicators disagree.
 *   - STRONG      (61 to 80):  Leaning bullish / healthy upward trend.
 *   - VERY_STRONG (81 to 100): High conviction bullish alignment across trend, volume, and momentum.
 *
 * DISCLAIMER:
 * These are quantitative signal aggregations for educational and analytical purposes,
 * not guaranteed price forecasts or financial advice.
 * ==================================================================================
 */

package com.quantstream.backend.analytics.scoring;

/**
 * Descriptive categorization of a Conviction Score (0–100).
 *
 * <p>These categories represent current quantitative signal alignment,
 * NOT guaranteed price movement or trading recommendations.</p>
 */
public enum ScoreCategory {
    VERY_WEAK("Very Weak", 0, 20),
    WEAK("Weak", 21, 40),
    NEUTRAL("Neutral", 41, 60),
    STRONG("Strong", 61, 80),
    VERY_STRONG("Very Strong", 81, 100);

    private final String displayName; // User-facing readable text: "Very Strong", etc.
    private final int minScore;        // Lower bound of the tier
    private final int maxScore;        // Upper bound of the tier

    ScoreCategory(String displayName, int minScore, int maxScore) {
        this.displayName = displayName;
        this.minScore = minScore;
        this.maxScore = maxScore;
    }

    public String getDisplayName() {
        return displayName;
    }

    public int getMinScore() {
        return minScore;
    }

    public int getMaxScore() {
        return maxScore;
    }

    /**
     * Given a raw numerical score (e.g. 74.5), returns the matching category (e.g. STRONG).
     */
    public static ScoreCategory fromScore(double score) {
        if (score <= 20.0) {
            return VERY_WEAK;
        } else if (score <= 40.0) {
            return WEAK;
        } else if (score <= 60.0) {
            return NEUTRAL;
        } else if (score <= 80.0) {
            return STRONG;
        } else {
            return VERY_STRONG;
        }
    }
}
