package com.franc1sdev.tally;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/** Checks how averages and marks are shown on the summary, the report and the PDF. */
public class StudentResultTest {

    @Test
    public void numbersShowNoUnnecessaryDecimals() {
        assertEquals("77", StudentResult.formatNumber(77));
        assertEquals("77.4", StudentResult.formatNumber(77.4));
        assertEquals("77.45", StudentResult.formatNumber(77.45));
        assertEquals("0", StudentResult.formatNumber(0));
        assertEquals("100", StudentResult.formatNumber(100));
    }
}
