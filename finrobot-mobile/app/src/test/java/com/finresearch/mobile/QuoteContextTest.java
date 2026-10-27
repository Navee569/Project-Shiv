package com.finresearch.mobile;

import org.junit.Test;
import static org.junit.Assert.*;

public class QuoteContextTest {
    @Test public void neverMixTickers() {
        assertTrue(QuoteContext.forRequestedSymbol("TSLA", "AAPL", "AAPL quote")
            .startsWith("Not available"));
    }
    @Test public void sameSymbolCanUseCachedQuote() {
        assertEquals("AAPL quote", QuoteContext.forRequestedSymbol(" aapl ", "AAPL", "AAPL quote"));
        assertEquals("NSE quote", QuoteContext.forRequestedSymbol("reliance.ns", "RELIANCE.NS", "NSE quote"));
    }
    @Test public void missingQuoteIsNotUsed() {
        assertTrue(QuoteContext.forRequestedSymbol("AAPL", "AAPL", "").startsWith("Not available"));
        assertTrue(QuoteContext.forRequestedSymbol(null, "AAPL", "price").startsWith("Not available"));
    }
}
