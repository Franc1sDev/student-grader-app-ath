package com.example.myfirstappath;

import android.content.Intent;
import android.os.Bundle;
import android.widget.EditText;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputLayout;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

public class marksPage extends AppCompatActivity {

    // Correct formats:
    // Admno like 25-0876 (2 digits, a dash, 4 digits)
    private static final Pattern ADMNO_PATTERN = Pattern.compile("^\\d{2}-\\d{4}$");
    // Name: at least two names (first and last), letters only (apostrophes and hyphens allowed inside names)
    private static final Pattern NAME_CHARACTERS = Pattern.compile("^[A-Za-z' \\-]+$");
    private static final Pattern FULL_NAME_PATTERN =
            Pattern.compile("^[A-Za-z][A-Za-z'\\-]*( [A-Za-z][A-Za-z'\\-]*)+$");
    // Mark: a number from 0 to 100, up to 2 decimal places (e.g. 75 or 67.5)
    private static final Pattern MARK_PATTERN = Pattern.compile("^\\d{1,3}(\\.\\d{1,2})?$");

    // Keys for keeping the trimester and a confirmed retake when the phone is rotated
    private static final String STATE_TRIMESTER = "trimester";
    private static final String STATE_CONFIRMED_RETAKE = "confirmedRetake";
    private static final String STATE_NAME_FROM_RECORD = "nameFromRecord";

    //1. Declare the views (UI components) for whom the logic applies
    TextInputLayout trimesterLayout, admnoLayout, nameLayout;
    MaterialAutoCompleteTextView trimesterInputField;
    EditText admnoInputField, nameInputField;
    TextInputLayout[] markLayouts = new TextInputLayout[5];   // C++, Networking, Android App Dev, Database, Cyber Security
    EditText[] markInputFields = new EditText[5];
    MaterialButton submitAndGrade, clearFields;

    DatabaseHelper db;
    FieldStyler styler;

    // "Jan-Apr 2026", "May-Aug 2026", "Sep-Dec 2026" (the year is the phone's current year)
    String[] trimesterOptions;
    String selectedTrimester; // null until one is chosen

    // Live state of each field:
    boolean admnoCorrect = false, nameCorrect = false;
    boolean[] markCorrect = new boolean[5];
    // Admno checks against the database:
    boolean admnoInThisTrimester = false;               // already has marks for the selected trimester -> blocked
    List<String> otherTrimesters = new ArrayList<>();    // trimesters the student already has marks for
    boolean isRetake = false;                            // known student, new trimester
    boolean retakeConfirmed = false;                     // the lecturer said "Yes, retaking"
    String nameOnRecord;                                 // the student's stored name (for a retaking student)
    String confirmedRetakeKey, askedRetakeKey;           // "admno|trimester" the lecturer confirmed / was asked about
    boolean nameFilledFromRecord = false;                // the name field was filled in from the database
    boolean refreshing = false;                          // stops refreshForm() running inside itself

    // "Touched" = the user has typed something in the field at least once.
    // We only show "required" errors on fields the user has already interacted with.
    boolean admnoTouched = false, nameTouched = false;
    boolean[] markTouched = new boolean[5];

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.marks_page);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            // systemBars + ime: when the keypad opens, the page shrinks above it and becomes scrollable,
            // so the keypad never covers a field or button
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.ime());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });

        db = new DatabaseHelper(this);

        //2. Tie the declared views to the actual IDs of same views that you set in XML:
        trimesterLayout = findViewById(R.id.til_trimester);
        trimesterInputField = findViewById(R.id.actxt_trimester);
        admnoLayout = findViewById(R.id.til_admno);
        nameLayout = findViewById(R.id.til_studentName);
        admnoInputField = findViewById(R.id.edtxt_admno);
        nameInputField = findViewById(R.id.edtxt_studentName);

        markLayouts[0] = findViewById(R.id.til_cpp);
        markLayouts[1] = findViewById(R.id.til_net);
        markLayouts[2] = findViewById(R.id.til_android);
        markLayouts[3] = findViewById(R.id.til_db);
        markLayouts[4] = findViewById(R.id.til_cyber);
        markInputFields[0] = findViewById(R.id.edtxt_cpp);
        markInputFields[1] = findViewById(R.id.edtxt_net);
        markInputFields[2] = findViewById(R.id.edtxt_android);
        markInputFields[3] = findViewById(R.id.edtxt_db);
        markInputFields[4] = findViewById(R.id.edtxt_cyber);

        submitAndGrade = findViewById(R.id.btn_submitAndgrade);
        clearFields = findViewById(R.id.btn_clearMarks);

        styler = new FieldStyler(this, admnoLayout); // loads the alert colours once

        // Trimester dropdown, with the current year worked out from the phone's date
        trimesterOptions = Trimester.optionsForCurrentYear();
        trimesterInputField.setSimpleItems(trimesterOptions);

        // After a rotation, put back the chosen trimester and a confirmed retake
        if (savedInstanceState != null) {
            String savedTrimester = savedInstanceState.getString(STATE_TRIMESTER);
            if (savedTrimester != null) trimesterInputField.setText(savedTrimester, false);
            confirmedRetakeKey = savedInstanceState.getString(STATE_CONFIRMED_RETAKE);
            nameFilledFromRecord = savedInstanceState.getBoolean(STATE_NAME_FROM_RECORD, false);
        }

        //3. Write code for behavior of those declared views:

            //3a. Real-time checking: every time any field changes (including the trimester), re-check the whole form
        trimesterInputField.addTextChangedListener(FieldStyler.onTextChanged(this::refreshForm));
        admnoInputField.addTextChangedListener(FieldStyler.onTextChanged(() -> {
            if (admnoInputField.length() > 0) admnoTouched = true;
            refreshForm();
        }));
        nameInputField.addTextChangedListener(FieldStyler.onTextChanged(() -> {
            if (nameInputField.length() > 0) nameTouched = true;
            refreshForm();
        }));
        for (int i = 0; i < markInputFields.length; i++) {
            final int index = i;
            markInputFields[i].addTextChangedListener(FieldStyler.onTextChanged(() -> {
                if (markInputFields[index].length() > 0) markTouched[index] = true;
                refreshForm();
            }));
        }

            //3b. Clear Fields button: clears the student's details and goes back to Admno (the trimester stays chosen)
        clearFields.setOnClickListener(view -> clearForm());

            //3c. Submit and Grade button: only clickable when everything is correct; asks for confirmation first
        submitAndGrade.setOnClickListener(view -> {
            refreshForm();
            if (!allDetailsCorrect()) return; // safety check
            showConfirmationPopup();
        });

        // Starting look of the form (neutral fields, blocked until a trimester is chosen, grey submit button):
        refreshForm();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(STATE_TRIMESTER, selectedTrimester);
        outState.putString(STATE_CONFIRMED_RETAKE, confirmedRetakeKey);
        outState.putBoolean(STATE_NAME_FROM_RECORD, nameFilledFromRecord);
    }

    @Override
    protected void onDestroy() {
        db.close();
        super.onDestroy();
    }

    //4. Re-checks every field, blocks/unblocks the other fields and updates the submit button
    private void refreshForm() {
        if (refreshing) return; // a field changed by this method itself: the running check already covers it
        refreshing = true;
        try {
            validateTrimester();

            // Nothing can be typed until the trimester is chosen (the admno checks depend on it)
            boolean trimesterChosen = selectedTrimester != null;
            styler.setBlocked(admnoLayout, !trimesterChosen);
            validateAdmno();

            // Retaking student: use the name already on record. Otherwise remove a name we filled in earlier.
            String typedName = cleanName(nameInputField.getText().toString());
            if (retakeConfirmed && !typedName.equals(nameOnRecord)) {
                nameInputField.setText(nameOnRecord);
                nameFilledFromRecord = true;
            } else if (!retakeConfirmed && nameFilledFromRecord) {
                nameInputField.setText("");
                nameFilledFromRecord = false;
                nameTouched = false;
            }

            // Block every other field when: no trimester yet, marks already exist for this trimester,
            // or the student is in another trimester and the lecturer hasn't confirmed the retake
            boolean blockOthers = !trimesterChosen || admnoInThisTrimester || (isRetake && !retakeConfirmed);
            styler.setBlocked(nameLayout, blockOthers || retakeConfirmed); // name on record can't be changed
            for (TextInputLayout markLayout : markLayouts) styler.setBlocked(markLayout, blockOthers);

            if (blockOthers) {
                nameCorrect = false;
                styler.showNeutral(nameLayout);
                for (int i = 0; i < markLayouts.length; i++) {
                    markCorrect[i] = false;
                    styler.showNeutral(markLayouts[i]);
                }
            } else {
                if (retakeConfirmed) {
                    nameCorrect = true;
                    styler.showSuccess(nameLayout, "Name on record");
                } else {
                    validateName();
                }
                for (int i = 0; i < markInputFields.length; i++) validateMark(i);
            }

            updateSubmitButton();
        } finally {
            refreshing = false;
        }

        askAboutRetakeIfNeeded();
    }

    private void validateTrimester() {
        String chosen = trimesterInputField.getText().toString().trim();
        selectedTrimester = Arrays.asList(trimesterOptions).contains(chosen) ? chosen : null;

        if (selectedTrimester == null) {
            styler.showInfo(trimesterLayout, "Choose the trimester first");
        } else {
            styler.showSuccess(trimesterLayout, "Marks will be saved under " + selectedTrimester);
        }
    }

    private void validateAdmno() {
        String typed = admnoInputField.getText().toString().trim();
        admnoCorrect = ADMNO_PATTERN.matcher(typed).matches();
        admnoInThisTrimester = false;
        otherTrimesters = new ArrayList<>();
        nameOnRecord = null;

        if (admnoCorrect && selectedTrimester != null) {
            List<String> trimesters = db.getStudentTrimesters(typed);
            admnoInThisTrimester = trimesters.contains(selectedTrimester);
            for (String t : trimesters) if (!t.equals(selectedTrimester)) otherTrimesters.add(t);
            if (!otherTrimesters.isEmpty()) nameOnRecord = db.getStudentName(typed);
        }
        isRetake = admnoCorrect && !admnoInThisTrimester && !otherTrimesters.isEmpty() && nameOnRecord != null;
        retakeConfirmed = isRetake && retakeKey().equals(confirmedRetakeKey);

        if (selectedTrimester == null) {
            styler.showNeutral(admnoLayout); // blocked until the trimester is chosen
        } else if (typed.isEmpty()) {
            if (admnoTouched) styler.showError(admnoLayout, "Admission number is required");
            else styler.showNeutral(admnoLayout);
        } else if (!admnoCorrect) {
            styler.showError(admnoLayout, "Use the format 25-0876");
        } else if (admnoInThisTrimester) {
            styler.showError(admnoLayout, "Student " + typed + " already has marks for " + selectedTrimester
                    + ". Marks cannot be added again");
        } else if (isRetake && retakeConfirmed) {
            styler.showSuccess(admnoLayout, "Retaking student (previously in " + joinTrimesters() + ")");
        } else if (isRetake) {
            styler.showError(admnoLayout, "Student already belongs to " + joinTrimesters() + ". Confirm the retake to continue");
        } else {
            styler.showSuccess(admnoLayout, "New student");
        }
    }

    private void validateName() {
        String typed = cleanName(nameInputField.getText().toString());
        nameCorrect = FULL_NAME_PATTERN.matcher(typed).matches();

        if (typed.isEmpty()) {
            if (nameTouched) styler.showError(nameLayout, "Student name is required");
            else styler.showNeutral(nameLayout);
        } else if (!NAME_CHARACTERS.matcher(typed).matches()) {
            styler.showError(nameLayout, "Use letters only");
        } else if (!nameCorrect) {
            styler.showError(nameLayout, "Enter the first and last name");
        } else {
            styler.showSuccess(nameLayout, "Name OK");
        }
    }

    private void validateMark(int i) {
        String typed = markInputFields[i].getText().toString().trim();
        markCorrect[i] = isValidMark(typed);

        if (typed.isEmpty()) {
            if (markTouched[i]) styler.showError(markLayouts[i], "Mark is required");
            else styler.showNeutral(markLayouts[i]);
        } else if (!markCorrect[i]) {
            styler.showError(markLayouts[i], "Enter 0 to 100");
        } else {
            styler.showSuccess(markLayouts[i], "Valid mark");
        }
    }

    private boolean isValidMark(String typed) {
        if (!MARK_PATTERN.matcher(typed).matches()) return false;
        double value = Double.parseDouble(typed);
        return value >= 0 && value <= 100;
    }

    private boolean allDetailsCorrect() {
        if (selectedTrimester == null) return false;
        if (!admnoCorrect || admnoInThisTrimester || (isRetake && !retakeConfirmed) || !nameCorrect) return false;
        for (boolean correct : markCorrect) if (!correct) return false;
        return true;
    }

    //5. Submit button: grey + disabled until ALL details are correct, then green + clickable.
    private void updateSubmitButton() {
        styler.setButtonReady(submitAndGrade, allDetailsCorrect());
    }

    //6. Retaking student: the admno already has marks in ANOTHER trimester, so ask the lecturer to confirm
    private void askAboutRetakeIfNeeded() {
        if (!isRetake || retakeConfirmed) return;
        String key = retakeKey();
        if (key.equals(askedRetakeKey)) return; // already asked about this admno + trimester
        askedRetakeKey = key;

        String admno = admnoInputField.getText().toString().trim();
        StringBuilder message = new StringBuilder();
        message.append("Student ").append(admno).append(" (").append(nameOnRecord).append(") already has marks for:\n");
        for (String t : otherTrimesters) message.append("   • ").append(t).append("\n");
        message.append("\nIs this a retaking student? Choose Yes to add marks for ").append(selectedTrimester).append(".");

        new MaterialAlertDialogBuilder(this)
                .setTitle("Retaking Student?")
                .setMessage(message.toString())
                .setCancelable(false)
                // NO: wrong admno, so it is cleared for the lecturer to type the right one
                .setNegativeButton("No", (dialog, which) -> {
                    askedRetakeKey = null;
                    admnoTouched = false;
                    admnoInputField.setText("");
                    admnoInputField.requestFocus();
                })
                // YES: unblock the marks; the name on record is filled in
                .setPositiveButton("Yes, Retaking", (dialog, which) -> {
                    confirmedRetakeKey = key;
                    refreshForm();
                    markInputFields[0].requestFocus();
                })
                .show();
    }

    private String retakeKey() {
        return admnoInputField.getText().toString().trim() + "|" + selectedTrimester;
    }

    private String joinTrimesters() {
        return String.join(", ", otherTrimesters);
    }

    //7. Confirmation pop up showing the details entered, with Yes / No
    private void showConfirmationPopup() {
        String admno = admnoInputField.getText().toString().trim();
        String name = cleanName(nameInputField.getText().toString());

        StringBuilder details = new StringBuilder();
        details.append("Trimester: ").append(selectedTrimester).append("\n");
        details.append("Admno: ").append(admno).append("\n");
        details.append("Name: ").append(name).append("\n");
        if (retakeConfirmed) details.append("Retaking student (previously in ").append(joinTrimesters()).append(")\n");
        details.append("\n");
        for (int i = 0; i < markInputFields.length; i++) {
            double mark = Double.parseDouble(markInputFields[i].getText().toString().trim());
            details.append(DatabaseHelper.SUBJECTS[i]).append(": ")
                    .append(StudentResult.formatNumber(mark)).append("\n");
        }
        details.append("\nDo you want to submit these details?");

        new MaterialAlertDialogBuilder(this)
                .setTitle("Confirm Submission")
                .setMessage(details.toString())
                // NO: the pop up closes and the details stay on the page, so they can be changed
                .setNegativeButton("No", (dialog, which) -> dialog.dismiss())
                // YES: save, grade and show the summary
                .setPositiveButton("Yes", (dialog, which) -> submitAndGradeStudent())
                .show();
    }

    //8. Save to the database, work out average / grade / remark, then go to the summary page
    private void submitAndGradeStudent() {
        String admno = admnoInputField.getText().toString().trim();
        String name = cleanName(nameInputField.getText().toString());
        String trimester = selectedTrimester;

        double[] marks = new double[markInputFields.length];
        for (int i = 0; i < markInputFields.length; i++) {
            marks[i] = Double.parseDouble(markInputFields[i].getText().toString().trim());
        }

        double average = GradeCalculator.average(marks);
        String grade = GradeCalculator.grade(average);
        String remark = GradeCalculator.remark(grade);

        boolean saved = db.saveStudentResult(admno, name, trimester, marks, average, grade, remark);
        if (!saved) {
            Toast.makeText(this, "Could not save. Student " + admno + " may already have marks for "
                    + trimester + ".", Toast.LENGTH_LONG).show();
            refreshForm();
            return;
        }

        // Clear the form so the next student can be added when the user comes back to this page
        clearForm();

        Intent moveToSummaryPage = new Intent(marksPage.this, summaryPage.class);
        moveToSummaryPage.putExtra(summaryPage.EXTRA_ADMNO, admno);
        moveToSummaryPage.putExtra(summaryPage.EXTRA_TRIMESTER, trimester);
        startActivity(moveToSummaryPage);
    }

    //9. Clears the student's details, unblocks the fields and puts the cursor back in Admno.
    //   The trimester stays chosen, so several students can be added for the same trimester.
    private void clearForm() {
        confirmedRetakeKey = null;
        askedRetakeKey = null;
        admnoInputField.setText("");
        nameInputField.setText("");
        nameFilledFromRecord = false;
        for (EditText markInputField : markInputFields) markInputField.setText("");

        // Back to a fresh, untouched form (no "required" errors after a deliberate clear)
        admnoTouched = false;
        nameTouched = false;
        for (int i = 0; i < markTouched.length; i++) markTouched[i] = false;

        refreshForm();
        admnoInputField.requestFocus();
    }

    //10. Small helpers:

    // Trims and turns repeated spaces into one: "  John   Kamau " -> "John Kamau"
    private String cleanName(String name) {
        return name.trim().replaceAll("\\s+", " ");
    }
}

/*
Grading criteria:
70-100: A
60-69: B
50-59: C
40-49: D
below 40: F

Remark:
A-D: PASS
F: FAIL
 */
