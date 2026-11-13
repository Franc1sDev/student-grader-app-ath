package com.franc1sdev.tally;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;

/** Checks the trimester names and their date order (used by the marks page and the report). */
public class TrimesterTest {

    @Test
    public void threeOptionsForTheCurrentYear() {
        int year = Trimester.currentYear();
        assertArrayEquals(new String[]{"Jan-Apr " + year, "May-Aug " + year, "Sep-Dec " + year},
                Trimester.optionsForCurrentYear());
    }

    @Test
    public void currentTrimesterIsOneOfTheOptions() {
        assertTrue(Arrays.asList(Trimester.optionsForCurrentYear()).contains(Trimester.current()));
    }

    @Test
    public void sortKeyPutsTrimestersInDateOrder() {
        assertEquals(20260, Trimester.sortKey("Jan-Apr 2026"));
        assertEquals(20261, Trimester.sortKey("May-Aug 2026"));
        assertEquals(20262, Trimester.sortKey("Sep-Dec 2026"));
        assertTrue(Trimester.sortKey("Sep-Dec 2026") < Trimester.sortKey("Jan-Apr 2027"));
    }

    @Test
    public void sortKeyOfBadTextIsZero() {
        assertEquals(0, Trimester.sortKey(null));
        assertEquals(0, Trimester.sortKey(""));
        assertEquals(0, Trimester.sortKey("Sep-Dec"));
        assertEquals(0, Trimester.sortKey("Sep-Dec twenty"));
    }
}
