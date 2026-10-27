package com.finresearch.mobile;

import org.junit.Test;
import static org.junit.Assert.*;

public class ValuationMathTest {
    @Test public void zeroGrowthFiveYearsEqualsPerpetuity() {
        assertEquals(1000.0, ValuationMath.dcfPerShare(100, 0.0, 0.10, 0.0, 0.0, 1.0), 0.0001);
    }
    @Test public void netDebtReducesEquityValue() {
        double withoutDebt = ValuationMath.dcfPerShare(100, 0.02, 0.10, 0.01, 0, 100);
        double withDebt = ValuationMath.dcfPerShare(100, 0.02, 0.10, 0.01, 300, 100);
        assertEquals(3.0, withoutDebt - withDebt, 0.0001);
    }
    @Test public void rejectsUnstableGrowth() {
        try { ValuationMath.dcfPerShare(100, 0.05, 0.04, 0.04, 0, 100); fail(); }
        catch (IllegalArgumentException expected) {}
        try { ValuationMath.dcfPerShare(100, 0.05, 0.10, 0.02, 0, 0); fail(); }
        catch (IllegalArgumentException expected) {}
    }
    @Test public void rejectsMissingOrNonfiniteInputs() {
        try { ValuationMath.dcfPerShare(Double.NaN, 0.05, 0.10, 0.02, 0, 100); fail(); }
        catch (IllegalArgumentException expected) {}
    }
}
