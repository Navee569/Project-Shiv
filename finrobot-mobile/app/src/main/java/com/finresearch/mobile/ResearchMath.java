package com.finresearch.mobile;

import java.util.List;

/** Deterministic indicators; no LLM-generated financial numbers. */
public final class ResearchMath {
    private ResearchMath() {}
    public static double sma(List<Double> prices, int days) {
        if (prices == null || days < 1 || prices.size() < days) return Double.NaN;
        double sum = 0;
        for (int i = prices.size() - days; i < prices.size(); i++) sum += prices.get(i);
        return sum / days;
    }
    /** Simple-window RSI, not Wilder-smoothed RSI. */
    public static double rsi(List<Double> prices, int days) {
        if (prices == null || days < 1 || prices.size() <= days) return Double.NaN;
        double gain = 0, loss = 0;
        for (int i = prices.size() - days; i < prices.size(); i++) {
            double d = prices.get(i) - prices.get(i - 1);
            if (d > 0) gain += d;
            else loss -= d;
        }
        if (gain == 0 && loss == 0) return 50.0;
        if (loss == 0) return 100.0;
        if (gain == 0) return 0.0;
        return 100.0 - 100.0 / (1.0 + gain / loss);
    }
    public static double percentChange(List<Double> prices) {
        if (prices == null || prices.size() < 2) return Double.NaN;
        double prev = prices.get(prices.size() - 2);
        if (prev == 0) return Double.NaN;
        return (prices.get(prices.size() - 1) / prev - 1.0) * 100.0;
    }
}
