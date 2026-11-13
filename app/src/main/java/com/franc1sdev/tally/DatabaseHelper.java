package com.franc1sdev.tally;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/*
 SQLite database for Tally.

 TABLES AND RELATIONSHIPS:

   lecturers (lecturer_id PK, lecturer_name, staff_number UNIQUE, email UNIQUE,
              password_hash, password_salt, activated, pending_reset, created_at)

   students  (admno PK, name)
       |  1
       |-------------< marks   (admno FK -> students, trimester, subject_id FK -> subjects, mark)
       |                  >-------- subjects (subject_id PK, subject_name)
       |  1
       |-------------< results (admno FK -> students, trimester, average, grade, remark)

   - A student can belong to MANY trimesters (e.g. a retaking student): one result per student per trimester,
     and one mark per student per trimester per subject.
   - A student can't have two results in the SAME trimester.
   - Deleting a student deletes his marks and results (ON DELETE CASCADE).

 VERSION HISTORY:
   1: students, subjects, marks, results (no trimesters)
   2: + lecturers table, + trimester column in marks and results (existing marks are kept and
      put in the trimester that was current when the app was updated)
*/
public class DatabaseHelper extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "tally.db";
    private static final int DATABASE_VERSION = 2;

    // Table and column names
    public static final String TABLE_STUDENTS = "students";
    public static final String TABLE_SUBJECTS = "subjects";
    public static final String TABLE_MARKS = "marks";
    public static final String TABLE_RESULTS = "results";
    public static final String TABLE_LECTURERS = "lecturers";

    public static final String COL_ADMNO = "admno";
    public static final String COL_NAME = "name";
    public static final String COL_TRIMESTER = "trimester";
    public static final String COL_SUBJECT_ID = "subject_id";
    public static final String COL_SUBJECT_NAME = "subject_name";
    public static final String COL_MARK = "mark";
    public static final String COL_AVERAGE = "average";
    public static final String COL_GRADE = "grade";
    public static final String COL_REMARK = "remark";

    public static final String COL_LECTURER_ID = "lecturer_id";
    public static final String COL_LECTURER_NAME = "lecturer_name";
    public static final String COL_STAFF_NUMBER = "staff_number";
    public static final String COL_EMAIL = "email";
    public static final String COL_PASSWORD_HASH = "password_hash";
    public static final String COL_PASSWORD_SALT = "password_salt";
    public static final String COL_ACTIVATED = "activated";
    public static final String COL_PENDING_RESET = "pending_reset";
    public static final String COL_CREATED_AT = "created_at";

    // The subjects, in the same order as the mark fields on the marks page.
    // subject_id in the database = position in this array + 1
    public static final String[] SUBJECTS = {
            "C++", "Networking", "Android App Dev", "Database", "Cyber Security"
    };

    public DatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onConfigure(SQLiteDatabase db) {
        super.onConfigure(db);
        db.setForeignKeyConstraintsEnabled(true); // SQLite only enforces relationships when this is on
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + TABLE_STUDENTS + " ("
                + COL_ADMNO + " TEXT PRIMARY KEY NOT NULL, "
                + COL_NAME + " TEXT NOT NULL)");

        db.execSQL("CREATE TABLE " + TABLE_SUBJECTS + " ("
                + COL_SUBJECT_ID + " INTEGER PRIMARY KEY, "
                + COL_SUBJECT_NAME + " TEXT NOT NULL UNIQUE)");

        createMarksTable(db);
        createResultsTable(db);
        createLecturersTable(db);

        // Fill in the subjects once, when the database is first created
        for (int i = 0; i < SUBJECTS.length; i++) {
            ContentValues subject = new ContentValues();
            subject.put(COL_SUBJECT_ID, i + 1);
            subject.put(COL_SUBJECT_NAME, SUBJECTS[i]);
            db.insert(TABLE_SUBJECTS, null, subject);
        }
    }

    private void createMarksTable(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + TABLE_MARKS + " ("
                + COL_ADMNO + " TEXT NOT NULL, "
                + COL_TRIMESTER + " TEXT NOT NULL, "
                + COL_SUBJECT_ID + " INTEGER NOT NULL, "
                + COL_MARK + " REAL NOT NULL CHECK (" + COL_MARK + " BETWEEN 0 AND 100), "
                + "PRIMARY KEY (" + COL_ADMNO + ", " + COL_TRIMESTER + ", " + COL_SUBJECT_ID + "), "
                + "FOREIGN KEY (" + COL_ADMNO + ") REFERENCES " + TABLE_STUDENTS + "(" + COL_ADMNO + ") ON DELETE CASCADE, "
                + "FOREIGN KEY (" + COL_SUBJECT_ID + ") REFERENCES " + TABLE_SUBJECTS + "(" + COL_SUBJECT_ID + "))");
    }

    private void createResultsTable(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + TABLE_RESULTS + " ("
                + COL_ADMNO + " TEXT NOT NULL, "
                + COL_TRIMESTER + " TEXT NOT NULL, "
                + COL_AVERAGE + " REAL NOT NULL, "
                + COL_GRADE + " TEXT NOT NULL, "
                + COL_REMARK + " TEXT NOT NULL, "
                + "PRIMARY KEY (" + COL_ADMNO + ", " + COL_TRIMESTER + "), "
                + "FOREIGN KEY (" + COL_ADMNO + ") REFERENCES " + TABLE_STUDENTS + "(" + COL_ADMNO + ") ON DELETE CASCADE)");
    }

    private void createLecturersTable(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + TABLE_LECTURERS + " ("
                + COL_LECTURER_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + COL_LECTURER_NAME + " TEXT NOT NULL, "
                + COL_STAFF_NUMBER + " TEXT NOT NULL UNIQUE COLLATE NOCASE, "
                + COL_EMAIL + " TEXT NOT NULL UNIQUE COLLATE NOCASE, "
                + COL_PASSWORD_HASH + " TEXT NOT NULL, "
                + COL_PASSWORD_SALT + " TEXT NOT NULL, "
                + COL_ACTIVATED + " INTEGER NOT NULL DEFAULT 0, "
                + COL_PENDING_RESET + " INTEGER NOT NULL DEFAULT 0, "
                + COL_CREATED_AT + " TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // Upgrades keep the existing data (onUpgrade already runs inside a transaction)
        if (oldVersion < 2) upgradeToVersion2(db);
    }

    // Version 1 -> 2: add the lecturers table and a trimester to every mark and result
    private void upgradeToVersion2(SQLiteDatabase db) {
        createLecturersTable(db);

        // The old marks had no trimester, so they go into the trimester that is current right now
        String existingMarksTrimester = Trimester.current();

        db.execSQL("ALTER TABLE " + TABLE_MARKS + " RENAME TO marks_v1");
        db.execSQL("ALTER TABLE " + TABLE_RESULTS + " RENAME TO results_v1");
        createMarksTable(db);
        createResultsTable(db);

        db.execSQL("INSERT INTO " + TABLE_MARKS + " (" + COL_ADMNO + ", " + COL_TRIMESTER + ", " + COL_SUBJECT_ID + ", " + COL_MARK + ")"
                + " SELECT " + COL_ADMNO + ", ?, " + COL_SUBJECT_ID + ", " + COL_MARK + " FROM marks_v1",
                new Object[]{existingMarksTrimester});
        db.execSQL("INSERT INTO " + TABLE_RESULTS + " (" + COL_ADMNO + ", " + COL_TRIMESTER + ", " + COL_AVERAGE + ", " + COL_GRADE + ", " + COL_REMARK + ")"
                + " SELECT " + COL_ADMNO + ", ?, " + COL_AVERAGE + ", " + COL_GRADE + ", " + COL_REMARK + " FROM results_v1",
                new Object[]{existingMarksTrimester});

        db.execSQL("DROP TABLE marks_v1");
        db.execSQL("DROP TABLE results_v1");
    }

    // ---------------------------------------------------------------------------------------------
    // STUDENTS
    // ---------------------------------------------------------------------------------------------

    /** The name stored for this admission number, or null if the student is not in the database. */
    public String getStudentName(String admno) {
        SQLiteDatabase db = getReadableDatabase();
        try (Cursor c = db.query(TABLE_STUDENTS, new String[]{COL_NAME},
                COL_ADMNO + " = ?", new String[]{admno}, null, null, null)) {
            return c.moveToFirst() ? c.getString(0) : null;
        }
    }

    /** Every trimester this student already has marks for, oldest first (empty if none). */
    public List<String> getStudentTrimesters(String admno) {
        List<String> trimesters = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        try (Cursor c = db.query(TABLE_RESULTS, new String[]{COL_TRIMESTER},
                COL_ADMNO + " = ?", new String[]{admno}, null, null, null)) {
            while (c.moveToNext()) trimesters.add(c.getString(0));
        }
        Collections.sort(trimesters, (a, b) -> Integer.compare(Trimester.sortKey(a), Trimester.sortKey(b)));
        return trimesters;
    }

    /** Every trimester that has at least one result, newest first (for the report filter). */
    public List<String> getTrimestersInUse() {
        List<String> trimesters = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        try (Cursor c = db.rawQuery("SELECT DISTINCT " + COL_TRIMESTER + " FROM " + TABLE_RESULTS, null)) {
            while (c.moveToNext()) trimesters.add(c.getString(0));
        }
        Collections.sort(trimesters, (a, b) -> Integer.compare(Trimester.sortKey(b), Trimester.sortKey(a)));
        return trimesters;
    }

    /**
     * Saves a student's marks (one row per subject) and result for ONE trimester, in ONE transaction:
     * either everything is saved, or nothing is.
     * If the student is already in the database (a retaking student), his stored name is kept.
     * @return true if saved, false if it failed (for example he already has marks for this trimester).
     */
    public boolean saveStudentResult(String admno, String name, String trimester, double[] marks,
                                     double average, String grade, String remark) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            ContentValues student = new ContentValues();
            student.put(COL_ADMNO, admno);
            student.put(COL_NAME, name);
            db.insertWithOnConflict(TABLE_STUDENTS, null, student, SQLiteDatabase.CONFLICT_IGNORE);

            for (int i = 0; i < marks.length; i++) {
                ContentValues mark = new ContentValues();
                mark.put(COL_ADMNO, admno);
                mark.put(COL_TRIMESTER, trimester);
                mark.put(COL_SUBJECT_ID, i + 1);
                mark.put(COL_MARK, marks[i]);
                db.insertOrThrow(TABLE_MARKS, null, mark);
            }

            ContentValues result = new ContentValues();
            result.put(COL_ADMNO, admno);
            result.put(COL_TRIMESTER, trimester);
            result.put(COL_AVERAGE, average);
            result.put(COL_GRADE, grade);
            result.put(COL_REMARK, remark);
            db.insertOrThrow(TABLE_RESULTS, null, result);

            db.setTransactionSuccessful();
            return true;
        } catch (SQLException e) {
            return false;
        } finally {
            db.endTransaction();
        }
    }

    /** One student's full record for one trimester (details, marks per subject and result), or null if not found. */
    public StudentResult getStudentResult(String admno, String trimester) {
        SQLiteDatabase db = getReadableDatabase();
        StudentResult student = null;

        try (Cursor c = db.rawQuery(
                "SELECT s." + COL_ADMNO + ", s." + COL_NAME + ", r." + COL_TRIMESTER + ", r." + COL_AVERAGE
                        + ", r." + COL_GRADE + ", r." + COL_REMARK
                        + " FROM " + TABLE_STUDENTS + " s"
                        + " JOIN " + TABLE_RESULTS + " r ON r." + COL_ADMNO + " = s." + COL_ADMNO
                        + " WHERE s." + COL_ADMNO + " = ? AND r." + COL_TRIMESTER + " = ?",
                new String[]{admno, trimester})) {
            if (c.moveToFirst()) student = readResult(c);
        }
        if (student == null) return null;

        double[] marks = new double[SUBJECTS.length];
        try (Cursor c = db.query(TABLE_MARKS, new String[]{COL_SUBJECT_ID, COL_MARK},
                COL_ADMNO + " = ? AND " + COL_TRIMESTER + " = ?", new String[]{admno, trimester},
                null, null, COL_SUBJECT_ID + " ASC")) {
            while (c.moveToNext()) {
                int index = c.getInt(0) - 1;
                if (index >= 0 && index < marks.length) marks[index] = c.getDouble(1);
            }
        }
        student.marks = marks;
        return student;
    }

    /** Every result of every student (one row per student per trimester), in admno order. */
    public List<StudentResult> getAllResults() {
        List<StudentResult> list = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        try (Cursor c = db.rawQuery(
                "SELECT s." + COL_ADMNO + ", s." + COL_NAME + ", r." + COL_TRIMESTER + ", r." + COL_AVERAGE
                        + ", r." + COL_GRADE + ", r." + COL_REMARK
                        + " FROM " + TABLE_STUDENTS + " s"
                        + " JOIN " + TABLE_RESULTS + " r ON r." + COL_ADMNO + " = s." + COL_ADMNO
                        + " ORDER BY s." + COL_ADMNO + " ASC", null)) {
            while (c.moveToNext()) list.add(readResult(c));
        }
        return list;
    }

    private static StudentResult readResult(Cursor c) {
        return new StudentResult(c.getString(0), c.getString(1), c.getString(2),
                c.getDouble(3), c.getString(4), c.getString(5));
    }

    // ---------------------------------------------------------------------------------------------
    // LECTURERS
    // ---------------------------------------------------------------------------------------------

    public boolean staffNumberExists(String staffNumber) {
        return getLecturer(COL_STAFF_NUMBER, staffNumber) != null;
    }

    public boolean emailExists(String email) {
        return getLecturer(COL_EMAIL, email) != null;
    }

    public Lecturer getLecturerByStaffNumber(String staffNumber) {
        return getLecturer(COL_STAFF_NUMBER, staffNumber);
    }

    public Lecturer getLecturerByEmail(String email) {
        return getLecturer(COL_EMAIL, email);
    }

    private Lecturer getLecturer(String column, String value) {
        if (value == null || value.isEmpty()) return null;
        SQLiteDatabase db = getReadableDatabase();
        try (Cursor c = db.query(TABLE_LECTURERS,
                new String[]{COL_LECTURER_ID, COL_LECTURER_NAME, COL_STAFF_NUMBER, COL_EMAIL,
                        COL_PASSWORD_HASH, COL_PASSWORD_SALT, COL_ACTIVATED, COL_PENDING_RESET},
                column + " = ? COLLATE NOCASE", new String[]{value}, null, null, null)) {
            if (!c.moveToFirst()) return null;
            return new Lecturer(c.getLong(0), c.getString(1), c.getString(2), c.getString(3),
                    c.getString(4), c.getString(5), c.getInt(6) == 1, c.getInt(7) == 1);
        }
    }

    /** @return true if saved, false if the staff number or email is already registered. */
    public boolean addLecturer(String name, String staffNumber, String email,
                               String passwordHash, String passwordSalt, boolean activated) {
        ContentValues lecturer = new ContentValues();
        lecturer.put(COL_LECTURER_NAME, name);
        lecturer.put(COL_STAFF_NUMBER, staffNumber);
        lecturer.put(COL_EMAIL, email);
        lecturer.put(COL_PASSWORD_HASH, passwordHash);
        lecturer.put(COL_PASSWORD_SALT, passwordSalt);
        lecturer.put(COL_ACTIVATED, activated ? 1 : 0);
        try {
            getWritableDatabase().insertOrThrow(TABLE_LECTURERS, null, lecturer);
            return true;
        } catch (SQLException e) {
            return false;
        }
    }

    public void setActivated(String email) {
        ContentValues values = new ContentValues();
        values.put(COL_ACTIVATED, 1);
        updateLecturer(email, values);
    }

    public void setPendingReset(String email, boolean pending) {
        ContentValues values = new ContentValues();
        values.put(COL_PENDING_RESET, pending ? 1 : 0);
        updateLecturer(email, values);
    }

    /** Saves a new password (already hashed). Opening a reset link proves the email is real, so it also activates. */
    public void updatePassword(String email, String passwordHash, String passwordSalt) {
        ContentValues values = new ContentValues();
        values.put(COL_PASSWORD_HASH, passwordHash);
        values.put(COL_PASSWORD_SALT, passwordSalt);
        values.put(COL_PENDING_RESET, 0);
        values.put(COL_ACTIVATED, 1);
        updateLecturer(email, values);
    }

    private void updateLecturer(String email, ContentValues values) {
        getWritableDatabase().update(TABLE_LECTURERS, values, COL_EMAIL + " = ? COLLATE NOCASE", new String[]{email});
    }
}
