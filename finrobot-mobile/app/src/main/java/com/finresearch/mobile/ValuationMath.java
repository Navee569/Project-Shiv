package com.finresearch.mobile;

/**
 * A five-year constant-growth free-cash-flow-to-firm DCF.
 * Assumptions and all monetary inputs must use the same currency and scale.
 * Per-share value = (enterprise value - net debt) / diluted shares.
 * Not a complete investment valuation: no scenario analysis or sector adjustments.
 */
public final class ValuationMath {
    private ValuationMath() {}
    public static double dcfPerShare(double fcf, double growth, double discount,
                                     double perpetualGrowth, double netDebt, double shares) {
        if (!Double.isFinite(fcf) || !Double.isFinite(growth) || !Double.isFinite(discount)
                || !Double.isFinite(perpetualGrowth) || !Double.isFinite(netDebt)
                || !Double.isFinite(shares) || fcf <= 0 || shares <= 0
                || growth <= -1 || discount <= 0 || perpetualGrowth <= -1
                || discount <= perpetualGrowth) {
            throw new IllegalArgumentException("Invalid DCF assumptions");
        }
        double pv = 0, projected = fcf;
        for (int year = 1; year <= 5; year++) {
            projected *= 1 + growth;
            pv += projected / Math.pow(1 + discount, year);
        }
        double terminalValue = projected * (1 + perpetualGrowth) / (discount - perpetualGrowth);
        pv += terminalValue / Math.pow(1 + discount, 5);
        double equityValue = pv - netDebt;
        double result = equityValue / shares;
        if (!Double.isFinite(result)) throw new IllegalArgumentException("DCF overflow");
        return result;
    }
}
