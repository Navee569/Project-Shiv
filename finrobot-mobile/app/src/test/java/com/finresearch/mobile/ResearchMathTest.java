package com.finresearch.mobile;

import org.junit.Test;
import java.util.Arrays;
import static org.junit.Assert.*;

public class ResearchMathTest {
    @Test public void smaRecentWindow() {
        assertEquals(3.0, ResearchMath.sma(Arrays.asList(1.0,2.0,3.0,4.0), 3), 0.00001);
        assertTrue(Double.isNaN(ResearchMath.sma(Arrays.asList(1.0), 2)));
    }
    @Test public void rsiEdgeCases() {
        assertEquals(100.0, ResearchMath.rsi(Arrays.asList(1.0,2.0,3.0), 2), 0.00001);
        assertEquals(0.0, ResearchMath.rsi(Arrays.asList(3.0,2.0,1.0), 2), 0.00001);
        assertEquals(50.0, ResearchMath.rsi(Arrays.asList(1.0,1.0,1.0), 2), 0.00001);
    }
    @Test public void changeAndInsufficientData() {
        assertEquals(10.0, ResearchMath.percentChange(Arrays.asList(10.0,11.0)), 0.00001);
        assertTrue(Double.isNaN(ResearchMath.percentChange(Arrays.asList(1.0))));
        assertTrue(Double.isNaN(ResearchMath.rsi(Arrays.asList(1.0),14)));
    }
}
