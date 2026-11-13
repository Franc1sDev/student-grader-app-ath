package com.franc1sdev.tally;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/** Checks the grading rules: the average of the 5 marks, the grade (A-F) and the remark (PASS/FAIL). */
public class GradeCalculatorTest {

    @Test
    public void averageOfFiveMarks() {
        assertEquals(77.0, GradeCalculator.average(new double[]{80, 75, 70, 85, 75}), 0.0);
    }

    @Test
    public void averageIsRoundedToTwoDecimals() {
        // 333.33 / 5 = 66.666 -> 66.67
        assertEquals(66.67, GradeCalculator.average(new double[]{66.67, 66.66, 66.67, 66.66, 66.67}), 0.0);
        // 69.996 rounds up to 70.00, so the grade is worked out from 70
        assertEquals(70.0, GradeCalculator.average(new double[]{70, 70, 70, 70, 69.98}), 0.0);
    }

    @Test
    public void gradeBoundaries() {
        assertEquals("A", GradeCalculator.grade(100));
        assertEquals("A", GradeCalculator.grade(70));
        assertEquals("B", GradeCalculator.grade(69.99));
        assertEquals("B", GradeCalculator.grade(60));
        assertEquals("C", GradeCalculator.grade(59.99));
        assertEquals("C", GradeCalculator.grade(50));
        assertEquals("D", GradeCalculator.grade(49.99));
        assertEquals("D", GradeCalculator.grade(40));
        assertEquals("F", GradeCalculator.grade(39.99));
        assertEquals("F", GradeCalculator.grade(0));
    }

    @Test
    public void remarkIsFailOnlyForF() {
        assertEquals("PASS", GradeCalculator.remark("A"));
        assertEquals("PASS", GradeCalculator.remark("B"));
        assertEquals("PASS", GradeCalculator.remark("C"));
        assertEquals("PASS", GradeCalculator.remark("D"));
        assertEquals("FAIL", GradeCalculator.remark("F"));
    }
}
