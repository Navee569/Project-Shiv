package com.finresearch.mobile;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/** Transparent keyword-only fallback; not an ML prediction. */
public final class HeadlineSentiment {
    private static final Set<String> POS = new HashSet<>(Arrays.asList(
        "beat","beats","profit","profits","growth","upgrade","upgraded",
        "surge","surges","bullish","record","gain","gains","strong","approval",
        "outperform","dividend","buyback","rally","raises","raised"));
    private static final Set<String> NEG = new HashSet<>(Arrays.asList(
        "miss","misses","loss","losses","decline","downgrade","downgraded",
        "plunge","bearish","warning","weak","fraud","lawsuit","default",
        "bankruptcy","investigation","cuts","cut","selloff","crash"));
    private HeadlineSentiment() {}
    public static double score(String text) {
        if (text == null || text.trim().isEmpty()) return 0.0;
        int pos = 0, neg = 0;
        for (String token : text.toLowerCase(java.util.Locale.ROOT).split("[^a-z]+")) {
            if (POS.contains(token)) pos++;
            if (NEG.contains(token)) neg++;
        }
        return pos + neg == 0 ? 0.0 : (double) (pos - neg) / (pos + neg);
    }
}
