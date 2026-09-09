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

    private final String displayName;
    private final int minScore;
    private final int maxScore;

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
