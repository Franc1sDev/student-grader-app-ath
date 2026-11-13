package com.franc1sdev.tally;

import android.content.Intent;
import android.os.Bundle;
import android.widget.EditText;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputLayout;

/*
 Opened when the lecturer taps the reset-password link in the email (see emailLinkHandler).
 The link is checked first; then the lecturer types the new password twice (live red / green checks),
 and Reset Password saves it with the email service AND in the database.
*/
public class resetPasswordPage extends AppCompatActivity {

    public static final String EXTRA_RESET_CODE = "resetCode"; // the code from the link in the email

    //1. Declare the views (UI components) for whom the logic applies
    TextInputLayout newPasswordLayout, confirmPasswordLayout;
    EditText newPasswordInputField, confirmPasswordInputField;
    MaterialButton resetPassword, cancel;
    TextView resetFor, backToLogin;

    DatabaseHelper db;
    FieldStyler styler;

    String resetCode;
    Lecturer lecturer; // whose password is being reset (null until the link has been checked)

    // Live state of each field:
    boolean newPasswordCorrect, confirmPasswordCorrect;
    boolean newPasswordTouched, confirmPasswordTouched;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.reset_password_page);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.ime());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });

        db = new DatabaseHelper(this);

        //2. Tie the declared views to the actual IDs of same views that you set in XML:
        newPasswordLayout = findViewById(R.id.til_newPassword);
        confirmPasswordLayout = findViewById(R.id.til_confirmNewPassword);
        newPasswordInputField = findViewById(R.id.edtxt_newPassword);
        confirmPasswordInputField = findViewById(R.id.edtxt_confirmNewPassword);
        resetPassword = findViewById(R.id.btn_resetPassword);
        cancel = findViewById(R.id.btn_cancelResetPassword);
        resetFor = findViewById(R.id.txtvw_resetFor);
        backToLogin = findViewById(R.id.txtvw_backToLogin);

        styler = new FieldStyler(this, newPasswordLayout);
        FieldStyler.underline(backToLogin);

        //3. Write code for behavior of those declared views:

            //3a. Real-time checking
        newPasswordInputField.addTextChangedListener(FieldStyler.onTextChanged(() -> {
            if (newPasswordInputField.length() > 0) newPasswordTouched = true;
            refreshForm();
        }));
        confirmPasswordInputField.addTextChangedListener(FieldStyler.onTextChanged(() -> {
            if (confirmPasswordInputField.length() > 0) confirmPasswordTouched = true;
            refreshForm();
        }));

            //3b. Cancel button: clears the fields
        cancel.setOnClickListener(view -> clearForm());

            //3c. Reset Password button: only clickable when both fields are correct
        resetPassword.setOnClickListener(view -> {
            refreshForm();
            if (!newPasswordCorrect || !confirmPasswordCorrect || lecturer == null) return; // safety check
            saveNewPassword(newPasswordInputField.getText().toString());
        });

        backToLogin.setOnClickListener(view -> goToLogin(lecturer == null ? null : lecturer.staffNumber));

        // Fields stay blocked until the link has been checked
        refreshForm();
        checkResetLink();
    }

    @Override
    protected void onDestroy() {
        db.close();
        super.onDestroy();
    }

    //4. Make sure the link is valid and belongs to a lecturer on this phone
    private void checkResetLink() {
        resetCode = getIntent().getStringExtra(EXTRA_RESET_CODE);
        if (resetCode == null) {
            goToLogin(null);
            return;
        }
        if (!EmailAuthService.isConfigured()) {
            Popups.showMessage(this, "Email Service Not Set Up", loginPage.notConfiguredMessage(), () -> goToLogin(null));
            return;
        }

        AlertDialog busy = Popups.showBusy(this, "Checking your reset link...");
        BackgroundTask.run(this, () -> EmailAuthService.checkResetCode(resetCode), (email, error) -> {
            Popups.dismiss(busy);
            if (error != null) {
                Popups.showMessage(this, "Reset Link Problem", EmailAuthService.friendlyMessage(error)
                        + "\n\nYou can ask for a new link with \"Forgot password?\" on the login page.", () -> goToLogin(null));
                return;
            }
            lecturer = db.getLecturerByEmail(email);
            if (lecturer == null) {
                Popups.showMessage(this, "No Account Found",
                        "No lecturer account on this phone uses " + email + ". Please sign up first.", () -> goToLogin(null));
                return;
            }
            resetFor.setText("Choose a new password for\n" + lecturer.name + " (" + lecturer.email + ")");
            refreshForm();
            newPasswordInputField.requestFocus();
        });
    }

    //5. Validation for each field:

    private void refreshForm() {
        boolean blocked = lecturer == null;
        styler.setBlocked(newPasswordLayout, blocked);
        styler.setBlocked(confirmPasswordLayout, blocked);

        validateNewPassword();
        validateConfirmPassword();
        styler.setButtonReady(resetPassword, !blocked && newPasswordCorrect && confirmPasswordCorrect);
    }

    private void validateNewPassword() {
        String typed = newPasswordInputField.getText().toString();
        String problem = typed.isEmpty() ? null : AccountRules.passwordProblem(typed);
        boolean sameAsOld = problem == null && lecturer != null && lecturer.passwordMatches(typed);
        newPasswordCorrect = !typed.isEmpty() && problem == null && !sameAsOld;

        if (typed.isEmpty()) {
            if (newPasswordTouched) styler.showError(newPasswordLayout, "New password is required");
            else styler.showNeutral(newPasswordLayout);
        } else if (problem != null) {
            styler.showError(newPasswordLayout, problem);
        } else if (sameAsOld) {
            styler.showError(newPasswordLayout, "Choose a password different from your old one");
        } else {
            styler.showSuccess(newPasswordLayout, "Strong password");
        }
    }

    private void validateConfirmPassword() {
        String typed = confirmPasswordInputField.getText().toString();
        boolean matches = typed.equals(newPasswordInputField.getText().toString());
        confirmPasswordCorrect = !typed.isEmpty() && matches && newPasswordCorrect;

        if (typed.isEmpty()) {
            if (confirmPasswordTouched) styler.showError(confirmPasswordLayout, "Please confirm the new password");
            else styler.showNeutral(confirmPasswordLayout);
        } else if (!matches) {
            styler.showError(confirmPasswordLayout, "Passwords do not match");
        } else if (!newPasswordCorrect) {
            styler.showNeutral(confirmPasswordLayout);
        } else {
            styler.showSuccess(confirmPasswordLayout, "Passwords match");
        }
    }

    //6. Save the new password with the email service and in the database
    private void saveNewPassword(String newPassword) {
        String staffNumber = lecturer.staffNumber;
        AlertDialog busy = Popups.showBusy(this, "Saving your new password...");
        BackgroundTask.run(this, () -> {
            String email = EmailAuthService.resetPassword(resetCode, newPassword);
            String salt = PasswordHasher.newSalt();
            try (DatabaseHelper database = new DatabaseHelper(getApplicationContext())) {
                database.updatePassword(email, PasswordHasher.hash(newPassword, salt), salt);
            }
            return email;
        }, (email, error) -> {
            Popups.dismiss(busy);
            if (error != null) {
                Popups.showMessage(this, "Could Not Reset Password", EmailAuthService.friendlyMessage(error), null);
                return;
            }
            Popups.showMessage(this, "Password Changed",
                    "Your password has been changed. Log in with your new password.",
                    () -> goToLogin(staffNumber));
        });
    }

    //7. Clears the fields and puts the cursor back in the first one
    private void clearForm() {
        newPasswordInputField.setText("");
        confirmPasswordInputField.setText("");
        newPasswordTouched = confirmPasswordTouched = false;
        refreshForm();
        newPasswordInputField.requestFocus();
    }

    private void goToLogin(String staffNumber) {
        Intent moveToLogin = new Intent(resetPasswordPage.this, loginPage.class);
        moveToLogin.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        if (staffNumber != null) moveToLogin.putExtra(loginPage.EXTRA_STAFF_NUMBER, staffNumber);
        startActivity(moveToLogin);
        finish();
    }
}
