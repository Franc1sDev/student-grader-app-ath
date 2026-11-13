package com.franc1sdev.tally;

import android.util.Patterns;

import java.util.Locale;
import java.util.regex.Pattern;

/** The rules a lecturer's sign-up details must follow (used by the sign up, login and reset password pages). */
public final class AccountRules {

    private AccountRules() { }

    public static final int MIN_PASSWORD_LENGTH = 8;

    // Lecturer name: at least two names, letters only (apostrophes, hyphens and dots allowed, e.g. "Dr. Jane O'Neil")
    private static final Pattern NAME_CHARACTERS = Pattern.compile("^[A-Za-z'. \\-]+$");
    private static final Pattern FULL_NAME_PATTERN =
            Pattern.compile("^[A-Za-z][A-Za-z'.\\-]*( [A-Za-z][A-Za-z'.\\-]*)+$");
    // Staff number: 3 to 15 letters / digits (dashes and slashes allowed), e.g. 9576 or KU/0453
    private static final Pattern STAFF_NUMBER_PATTERN = Pattern.compile("^[A-Za-z0-9/\\-]{3,15}$");

    /** Trims and turns repeated spaces into one: "  Jane   Doe " -> "Jane Doe" */
    public static String cleanName(String name) {
        return name.trim().replaceAll("\\s+", " ");
    }

    /** null if the name is fine, otherwise what is wrong with it. */
    public static String nameProblem(String cleanedName) {
        if (!NAME_CHARACTERS.matcher(cleanedName).matches()) return "Use letters only";
        if (!FULL_NAME_PATTERN.matcher(cleanedName).matches()) return "Enter the first and last name";
        return null;
    }

    /** Staff numbers are stored in capitals so "ku/0453" and "KU/0453" are the same lecturer. */
    public static String normaliseStaffNumber(String staffNumber) {
        return staffNumber.trim().toUpperCase(Locale.ROOT);
    }

    public static boolean isValidStaffNumber(String normalisedStaffNumber) {
        return STAFF_NUMBER_PATTERN.matcher(normalisedStaffNumber).matches();
    }

    /** Emails are stored in small letters so "Jane@Uni.ac.ke" and "jane@uni.ac.ke" are the same lecturer. */
    public static String normaliseEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    public static boolean isValidEmail(String normalisedEmail) {
        return Patterns.EMAIL_ADDRESS.matcher(normalisedEmail).matches();
    }

    /** null if the password is strong enough, otherwise what is missing. */
    public static String passwordProblem(String password) {
        if (password.length() < MIN_PASSWORD_LENGTH) return "Use at least " + MIN_PASSWORD_LENGTH + " characters";
        if (!password.matches(".*[A-Za-z].*")) return "Include at least one letter";
        if (!password.matches(".*\\d.*")) return "Include at least one number";
        if (!password.equals(password.trim())) return "Remove spaces at the start or end";
        return null;
    }
}
