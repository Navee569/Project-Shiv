package com.finresearch.mobile;

import java.util.Locale;

/** Prevents cached financial data from being silently applied to a different ticker. */
public final class QuoteContext {
    private QuoteContext() {}

    public static String forRequestedSymbol(String requested, String cachedSymbol, String cachedQuote) {
        if (requested == null || cachedSymbol == null || cachedQuote == null || cachedQuote.isEmpty()) {
            return "Not available (no matching fetched quote)";
        }
        String ticker = requested.trim().toUpperCase(Locale.ROOT);
        if (ticker.isEmpty() || !ticker.equals(cachedSymbol.trim().toUpperCase(Locale.ROOT))) {
            return "Not available (ticker differs from cached quote; fetch this ticker first)";
        }
        return cachedQuote;
    }
}
