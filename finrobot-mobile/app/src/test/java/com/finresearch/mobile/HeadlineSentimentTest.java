package com.finresearch.mobile;

import org.junit.Test;
import static org.junit.Assert.*;

public class HeadlineSentimentTest {
    @Test public void positiveAndNegative() {
        assertEquals(1.0, HeadlineSentiment.score("Company profits surge"), 0.0001);
        assertEquals(-1.0, HeadlineSentiment.score("Company losses plunge"), 0.0001);
    }
    @Test public void mixedAndEmpty() {
        assertEquals(0.0, HeadlineSentiment.score("profit loss"), 0.0001);
        assertEquals(0.0, HeadlineSentiment.score(""), 0.0001);
        assertEquals(0.0, HeadlineSentiment.score(null), 0.0001);
    }
}
