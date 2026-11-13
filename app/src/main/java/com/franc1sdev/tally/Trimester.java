package com.franc1sdev.tally;

import java.util.Calendar;

/*
 Trimesters are stored as text, e.g. "Jan-Apr 2026", "May-Aug 2026", "Sep-Dec 2026".
 The year is always worked out from the phone's date, so the list stays current every year.
*/
public final class Trimester {

    private Trimester() { }

    public static final String[] PERIODS = {"Jan-Apr", "May-Aug", "Sep-Dec"};

    public static int currentYear() {
        return Calendar.getInstance().get(Calendar.YEAR);
    }

    /** The three trimesters of the current year, e.g. {"Jan-Apr 2026", "May-Aug 2026", "Sep-Dec 2026"}. */
    public static String[] optionsForCurrentYear() {
        int year = currentYear();
        String[] options = new String[PERIODS.length];
        for (int i = 0; i < PERIODS.length; i++) options[i] = PERIODS[i] + " " + year;
        return options;
    }

    /** The trimester today falls in, e.g. "Sep-Dec 2026" in November 2026. */
    public static String current() {
        int month = Calendar.getInstance().get(Calendar.MONTH); // 0 = January
        return PERIODS[month / 4] + " " + currentYear();
    }

    /** Number for sorting trimesters in date order: "Jan-Apr 2026" -> 20260, "Sep-Dec 2026" -> 20262. */
    public static int sortKey(String trimester) {
        if (trimester == null) return 0;
        String[] parts = trimester.trim().split(" ");
        if (parts.length != 2) return 0;
        int period = -1;
        for (int i = 0; i < PERIODS.length; i++) if (PERIODS[i].equals(parts[0])) period = i;
        try {
            return Integer.parseInt(parts[1]) * 10 + Math.max(period, 0);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
