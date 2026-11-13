package com.franc1sdev.tally;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/** One student's result for one trimester, as read from the database. */
public class StudentResult {

    public final String admno;
    public final String name;
    public final String trimester; // e.g. "Sep-Dec 2026"
    public final double average;
    public final String grade;
    public final String remark;
    public double[] marks; // per subject, same order as DatabaseHelper.SUBJECTS (only filled for single-student reads)

    public StudentResult(String admno, String name, String trimester, double average, String grade, String remark) {
        this.admno = admno;
        this.name = name;
        this.trimester = trimester;
        this.average = average;
        this.grade = grade;
        this.remark = remark;
    }

    /** Formats a number without unnecessary decimals: 77 -> "77", 77.4 -> "77.4", 77.45 -> "77.45". */
    public static String formatNumber(double value) {
        DecimalFormat format = new DecimalFormat("0.##", DecimalFormatSymbols.getInstance(Locale.US));
        return format.format(value);
    }
}
