package com.example.myfirstappath;

import java.math.BigDecimal;
import java.math.RoundingMode;

/*
 Grading criteria:
 70-100:   A, PASS
 60-69:    B, PASS
 50-59:    C, PASS
 40-49:    D, PASS
 below 40: F, FAIL
*/
public final class GradeCalculator {

    private GradeCalculator() { }

    /** Average of the marks, rounded to 2 decimal places (the grade is worked out from this same rounded value). */
    public static double average(double[] marks) {
        BigDecimal sum = BigDecimal.ZERO;
        for (double mark : marks) sum = sum.add(BigDecimal.valueOf(mark));
        return sum.divide(BigDecimal.valueOf(marks.length), 2, RoundingMode.HALF_UP).doubleValue();
    }

    public static String grade(double average) {
        if (average >= 70) return "A";
        if (average >= 60) return "B";
        if (average >= 50) return "C";
        if (average >= 40) return "D";
        return "F";
    }

    public static String remark(String grade) {
        return grade.equals("F") ? "FAIL" : "PASS";
    }
}
