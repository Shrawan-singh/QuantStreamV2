package com.quantstream.backend.analytics.scoring;

import com.quantstream.backend.analytics.indicator.IndicatorResult;
import com.quantstream.backend.analytics.indicator.Signal;
import com.quantstream.backend.analytics.state.MarketState;
import com.quantstream.backend.config.ScoringProperties;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Deterministic, continuous scoring engine that combines quantitative technical
 * indicators into a unified Conviction Score on a continuous 0–100 scale.
 *
 * <h3>Continuous Normalization Model:</h3>
 * <ul>
 *   <li><b>Trend (0–100)</b>: Linear mapping of price divergence relative to 20-period SMA:
 *       <pre>diffPct = ((Price - SMA) / SMA) * 100.0
 * trendScore = clamp(50.0 + (diffPct / 2.0) * 50.0, 0.0, 100.0)</pre>
 *       A divergence of &plusmn;2.0% maps continuously to [0, 100], with 0.0% divergence at 50.0 (neutral).
 *   </li>
 *   <li><b>Momentum (0–100)</b>: Linear mapping of 10-period price rate of change:
 *       <pre>momentumScore = clamp(50.0 + (momentumPct / 2.0) * 50.0, 0.0, 100.0)</pre>
 *       A price change of &plusmn;2.0% over lookback maps continuously to [0, 100], with 0.0% change at 50.0.
 *   </li>
 *   <li><b>RSI (0–100)</b>: Direct Wilder RSI index value:
 *       <pre>rsiScore = clamp(RSI, 0.0, 100.0)</pre>
 *   </li>
 *   <li><b>Relative Volume (0–100)</b>: Linear mapping of volume ratio relative to 20-period average:
 *       <pre>volumeScore = clamp(RVOL * 50.0, 0.0, 100.0)</pre>
 *       Baseline 1.0x volume maps to 50.0; elevated 2.0x volume maps to 100.0.
 *   </li>
 * </ul>
 *
 * <p>Composite Conviction Score:
 * <pre>finalScore = (w_trend * trendScore + w_mom * momentumScore + w_rsi * rsiScore + w_vol * volumeScore) / totalWeight</pre>
 * Clamped to [0.0, 100.0] and rounded to one decimal place.</p>
 */
@Component
public class ConvictionScoreEngine {

    private final ScoringProperties properties;

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

        boolean anyReady = (smaResult != null && smaResult.ready()) ||
                (emaResult != null && emaResult.ready()) ||
                (rsiResult != null && rsiResult.ready()) ||
                (momentumResult != null && momentumResult.ready()) ||
                (rvolResult != null && rvolResult.ready());

        if (!anyReady) {
            return ConvictionScore.notReady(symbol, timestamp);
        }

        double totalWeight = properties.totalWeight();
        if (totalWeight <= 0.0) {
            totalWeight = 100.0;
        }

        double wTrend = properties.trendWeight() / totalWeight;
        double wMomentum = properties.momentumWeight() / totalWeight;
        double wRsi = properties.rsiWeight() / totalWeight;
        double wVolume = properties.volumeWeight() / totalWeight;

        // 1. Trend Factor Score [0 - 100]
        double trendScore = 50.0;
        Signal trendSignal = Signal.NOT_READY;
        IndicatorResult trendIndicator = (smaResult != null && smaResult.ready()) ? smaResult : emaResult;
        if (trendIndicator != null && trendIndicator.ready() && snapshot != null && snapshot.latestPrice() != null) {
            double benchmarkVal = trendIndicator.value();
            if (benchmarkVal > 0.0) {
                double currentPrice = snapshot.latestPrice().doubleValue();
                double diffPct = ((currentPrice - benchmarkVal) / benchmarkVal) * 100.0;
                trendScore = clamp(50.0 + (diffPct / 2.0) * 50.0, 0.0, 100.0);
            }
            trendSignal = scoreToSignal(trendScore);
        }

        // 2. Momentum Factor Score [0 - 100]
        double momentumScore = 50.0;
        Signal momentumSignal = Signal.NOT_READY;
        if (momentumResult != null && momentumResult.ready()) {
            double momentumVal = momentumResult.value();
            momentumScore = clamp(50.0 + (momentumVal / 2.0) * 50.0, 0.0, 100.0);
            momentumSignal = scoreToSignal(momentumScore);
        }

        // 3. RSI Factor Score [0 - 100]
        double rsiScore = 50.0;
        Signal rsiSignal = Signal.NOT_READY;
        if (rsiResult != null && rsiResult.ready()) {
            rsiScore = clamp(rsiResult.value(), 0.0, 100.0);
            rsiSignal = scoreToSignal(rsiScore);
        }

        // 4. Relative Volume Factor Score [0 - 100]
        double volumeScore = 50.0;
        Signal volumeSignal = Signal.NOT_READY;
        if (rvolResult != null && rvolResult.ready()) {
            double rvol = rvolResult.value();
            volumeScore = clamp(rvol * 50.0, 0.0, 100.0);
            volumeSignal = scoreToSignal(volumeScore);
        }

        // Round factor scores to 1 decimal place
        trendScore = roundOneDecimal(trendScore);
        momentumScore = roundOneDecimal(momentumScore);
        rsiScore = roundOneDecimal(rsiScore);
        volumeScore = roundOneDecimal(volumeScore);

        // Weighted contributions
        double trendContribution = roundOneDecimal(wTrend * trendScore);
        double momentumContribution = roundOneDecimal(wMomentum * momentumScore);
        double rsiContribution = roundOneDecimal(wRsi * rsiScore);
        double volumeContribution = roundOneDecimal(wVolume * volumeScore);

        // Composite Final Score: rounded to 1 decimal place
        double rawFinalScore = (wTrend * trendScore) + (wMomentum * momentumScore) + (wRsi * rsiScore) + (wVolume * volumeScore);
        double compositeScore = roundOneDecimal(clamp(rawFinalScore, 0.0, 100.0));

        ScoreCategory category = ScoreCategory.fromScore(compositeScore);

        // Build explainability list
        List<String> explanations = new ArrayList<>(4);
        if (trendIndicator != null && trendIndicator.ready() && snapshot != null) {
            explanations.add(String.format("Trend: %.1f (Price ₹%s vs SMA ₹%.2f)",
                    trendScore, snapshot.latestPrice(), trendIndicator.value()));
        } else {
            explanations.add("Trend: 50.0 (Awaiting sufficient history for SMA)");
        }

        if (momentumResult != null && momentumResult.ready()) {
            explanations.add(String.format("Momentum: %.1f (Lookback change: %+.2f%%)",
                    momentumScore, momentumResult.value()));
        } else {
            explanations.add("Momentum: 50.0 (Awaiting lookback period)");
        }

        if (rsiResult != null && rsiResult.ready()) {
            explanations.add(String.format("RSI: %.1f (14-period index: %.1f)",
                    rsiScore, rsiResult.value()));
        } else {
            explanations.add("RSI: 50.0 (Awaiting 14-period warm-up)");
        }

        if (rvolResult != null && rvolResult.ready()) {
            explanations.add(String.format("Volume: %.1f (%.2fx of 20-period baseline)",
                    volumeScore, rvolResult.value()));
        } else {
            explanations.add("Volume: 50.0 (Awaiting volume history)");
        }

        Map<String, Signal> signals = new HashMap<>();
        signals.put("trend", trendSignal);
        signals.put("momentum", momentumSignal);
        signals.put("rsi", rsiSignal);
        signals.put("volume", volumeSignal);

        return new ConvictionScore(
                symbol,
                compositeScore,
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
                timestamp
        );
    }

    private Signal scoreToSignal(double score) {
        if (score >= 60.0) return Signal.POSITIVE;
        if (score <= 40.0) return Signal.NEGATIVE;
        return Signal.NEUTRAL;
    }

    private double clamp(double val, double min, double max) {
        return Math.max(min, Math.min(max, val));
    }

    private double roundOneDecimal(double val) {
        return BigDecimal.valueOf(val).setScale(1, RoundingMode.HALF_UP).doubleValue();
    }
}
