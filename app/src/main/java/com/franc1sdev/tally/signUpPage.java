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
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputLayout;

/*
 Lecturer sign up:
   1. Every field is checked live (red / green), and Sign Up only turns green when all are correct.
   2. Sign Up -> confirmation pop up -> the account is created with the email service, which emails an
      activation link, and the details are saved in the database (password hashed, not activated yet).
   3. Tapping the link in the email opens the app (emailLinkHandler -> loginPage), which activates the account.
*/
public class signUpPage extends AppCompatActivity {

    //1. Declare the views (UI components) for whom the logic applies
    TextInputLayout nameLayout, staffNumberLayout, emailLayout, passwordLayout, confirmPasswordLayout;
    EditText nameInputField, staffNumberInputField, emailInputField, passwordInputField, confirmPasswordInputField;
    MaterialButton signUp, cancel;
    TextView loginLink;

    DatabaseHelper db;
    FieldStyler styler;

    // Live state of each field:
    boolean nameCorrect, staffNumberCorrect, emailCorrect, passwordCorrect, confirmPasswordCorrect;
    // "Touched" = the user has typed something in the field at least once.
    boolean nameTouched, staffNumberTouched, emailTouched, passwordTouched, confirmPasswordTouched;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.sign_up_page);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            // systemBars + ime: when the keypad opens, the page shrinks above it and becomes scrollable
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.ime());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });

        db = new DatabaseHelper(this);

        //2. Tie the declared views to the actual IDs of same views that you set in XML:
        nameLayout = findViewById(R.id.til_lecturerName);
        staffNumberLayout = findViewById(R.id.til_signUpStaffNumber);
        emailLayout = findViewById(R.id.til_signUpEmail);
        passwordLayout = findViewById(R.id.til_signUpPassword);
        confirmPasswordLayout = findViewById(R.id.til_confirmPassword);
        nameInputField = findViewById(R.id.edtxt_lecturerName);
        staffNumberInputField = findViewById(R.id.edtxt_signUpStaffNumber);
        emailInputField = findViewById(R.id.edtxt_signUpEmail);
        passwordInputField = findViewById(R.id.edtxt_signUpPassword);
        confirmPasswordInputField = findViewById(R.id.edtxt_confirmPassword);
        signUp = findViewById(R.id.btn_signUp);
        cancel = findViewById(R.id.btn_cancelSignUp);
        loginLink = findViewById(R.id.txtvw_loginLink);

        styler = new FieldStyler(this, nameLayout);
        FieldStyler.underline(loginLink);

        //3. Write code for behavior of those declared views:

            //3a. Real-time checking: every time any field changes, re-check the whole form
            // (e.g. changing the password also re-checks Confirm Password)
        nameInputField.addTextChangedListener(FieldStyler.onTextChanged(() -> {
            if (nameInputField.length() > 0) nameTouched = true;
            refreshForm();
        }));
        staffNumberInputField.addTextChangedListener(FieldStyler.onTextChanged(() -> {
            if (staffNumberInputField.length() > 0) staffNumberTouched = true;
            refreshForm();
        }));
        emailInputField.addTextChangedListener(FieldStyler.onTextChanged(() -> {
            if (emailInputField.length() > 0) emailTouched = true;
            refreshForm();
        }));
        passwordInputField.addTextChangedListener(FieldStyler.onTextChanged(() -> {
            if (passwordInputField.length() > 0) passwordTouched = true;
            refreshForm();
        }));
        confirmPasswordInputField.addTextChangedListener(FieldStyler.onTextChanged(() -> {
            if (confirmPasswordInputField.length() > 0) confirmPasswordTouched = true;
            refreshForm();
        }));

            //3b. Cancel button: clears every field and goes back to the first one
        cancel.setOnClickListener(view -> clearForm());

            //3c. Sign Up button: only clickable when everything is correct; asks for confirmation first
        signUp.setOnClickListener(view -> {
            refreshForm();
            if (!allDetailsCorrect()) return; // safety check
            showConfirmationPopup();
        });

            //3d. "I already have an account, let me in" goes back to the login page
        loginLink.setOnClickListener(view -> goToLogin(null));

        // Starting look of the form (neutral fields, grey disabled Sign Up button):
        refreshForm();
    }

    @Override
    protected void onDestroy() {
        db.close();
        super.onDestroy();
    }

    //4. Validation for each field:

    private void refreshForm() {
        validateName();
        validateStaffNumber();
        validateEmail();
        validatePassword();
        validateConfirmPassword();
        styler.setButtonReady(signUp, allDetailsCorrect());
    }

    private void validateName() {
        String typed = AccountRules.cleanName(nameInputField.getText().toString());
        String problem = typed.isEmpty() ? null : AccountRules.nameProblem(typed);
        nameCorrect = !typed.isEmpty() && problem == null;

        if (typed.isEmpty()) {
            if (nameTouched) styler.showError(nameLayout, "Lecturer name is required");
            else styler.showNeutral(nameLayout);
        } else if (problem != null) {
            styler.showError(nameLayout, problem);
        } else {
            styler.showSuccess(nameLayout, "Name OK");
        }
    }

    private void validateStaffNumber() {
        String typed = AccountRules.normaliseStaffNumber(staffNumberInputField.getText().toString());
        boolean validFormat = AccountRules.isValidStaffNumber(typed);
        boolean taken = validFormat && db.staffNumberExists(typed);
        staffNumberCorrect = validFormat && !taken;

        if (typed.isEmpty()) {
            if (staffNumberTouched) styler.showError(staffNumberLayout, "Staff number is required");
            else styler.showNeutral(staffNumberLayout);
        } else if (!validFormat) {
            styler.showError(staffNumberLayout, "Use 3 to 15 letters or numbers (e.g. 9576)");
        } else if (taken) {
            styler.showError(staffNumberLayout, "Staff number already registered. Log in instead");
        } else {
            styler.showSuccess(staffNumberLayout, "Staff number available");
        }
    }

    private void validateEmail() {
        String typed = AccountRules.normaliseEmail(emailInputField.getText().toString());
        boolean validFormat = AccountRules.isValidEmail(typed);
        boolean taken = validFormat && db.emailExists(typed);
        emailCorrect = validFormat && !taken;

        if (typed.isEmpty()) {
            if (emailTouched) styler.showError(emailLayout, "Email is required");
            else styler.showNeutral(emailLayout);
        } else if (!validFormat) {
            styler.showError(emailLayout, "Enter a valid email address");
        } else if (taken) {
            styler.showError(emailLayout, "Email already registered. Log in instead");
        } else {
            styler.showSuccess(emailLayout, "Email OK. The activation link will be sent here");
        }
    }

    private void validatePassword() {
        String typed = passwordInputField.getText().toString();
        String problem = typed.isEmpty() ? null : AccountRules.passwordProblem(typed);
        passwordCorrect = !typed.isEmpty() && problem == null;

        if (typed.isEmpty()) {
            if (passwordTouched) styler.showError(passwordLayout, "Password is required");
            else styler.showNeutral(passwordLayout);
        } else if (problem != null) {
            styler.showError(passwordLayout, problem);
        } else {
            styler.showSuccess(passwordLayout, "Strong password");
        }
    }

    private void validateConfirmPassword() {
        String typed = confirmPasswordInputField.getText().toString();
        boolean matches = typed.equals(passwordInputField.getText().toString());
        confirmPasswordCorrect = !typed.isEmpty() && matches && passwordCorrect;

        if (typed.isEmpty()) {
            if (confirmPasswordTouched) styler.showError(confirmPasswordLayout, "Please confirm the password");
            else styler.showNeutral(confirmPasswordLayout);
        } else if (!matches) {
            styler.showError(confirmPasswordLayout, "Passwords do not match");
        } else if (!passwordCorrect) {
            styler.showNeutral(confirmPasswordLayout); // they match, but the password itself still needs fixing
        } else {
            styler.showSuccess(confirmPasswordLayout, "Passwords match");
        }
    }

    private boolean allDetailsCorrect() {
        return nameCorrect && staffNumberCorrect && emailCorrect && passwordCorrect && confirmPasswordCorrect;
    }

    //5. Confirmation pop up showing the details entered, with Yes / No
    private void showConfirmationPopup() {
        String name = AccountRules.cleanName(nameInputField.getText().toString());
        String staffNumber = AccountRules.normaliseStaffNumber(staffNumberInputField.getText().toString());
        String email = AccountRules.normaliseEmail(emailInputField.getText().toString());

        new MaterialAlertDialogBuilder(this)
                .setTitle("Confirm Sign Up")
                .setMessage("Name: " + name + "\n"
                        + "Staff Number: " + staffNumber + "\n"
                        + "Email: " + email + "\n\n"
                        + "An activation link will be sent to this email.\n\nDo you want to sign up?")
                // NO: the pop up closes and the details stay on the page, so they can be changed
                .setNegativeButton("No", (dialog, which) -> dialog.dismiss())
                .setPositiveButton("Yes", (dialog, which) -> createAccount(name, staffNumber, email,
                        passwordInputField.getText().toString()))
                .show();
    }

    //6. Create the account: email service first (it sends the activation link), then the database
    private void createAccount(String name, String staffNumber, String email, String password) {
        String salt = PasswordHasher.newSalt();
        String passwordHash = PasswordHasher.hash(password, salt);

        if (!EmailAuthService.isConfigured()) {
            if (BuildConfig.DEBUG) {
                // Development only: lets the app be tried before the email service is set up
                boolean saved = db.addLecturer(name, staffNumber, email, passwordHash, salt, true);
                if (!saved) {
                    Popups.showMessage(this, "Could Not Sign Up", "This staff number or email is already registered.", this::refreshForm);
                    return;
                }
                Popups.showMessage(this, "Account Created (Test Mode)",
                        "The email service is not set up yet, so no activation email was sent and the account "
                                + "was activated straight away. This only happens in debug builds.\n\n"
                                + "See EMAIL_SETUP.md to turn on activation emails.",
                        () -> goToLogin(staffNumber));
            } else {
                Popups.showMessage(this, "Sign Up Unavailable", loginPage.notConfiguredMessage(), null);
            }
            return;
        }

        AlertDialog busy = Popups.showBusy(this, "Creating your account...");
        BackgroundTask.run(this, () -> {
            String idToken;
            boolean alreadyActivated = false;
            try {
                idToken = EmailAuthService.createAccount(email, password);
            } catch (EmailAuthService.AuthException e) {
                // The email service already knows this email (e.g. the app was reinstalled):
                // reuse that account if the password is the same.
                if (!"EMAIL_EXISTS".equals(e.code)) throw e;
                try {
                    idToken = EmailAuthService.signIn(email, password);
                } catch (EmailAuthService.AuthException wrongPassword) {
                    throw e; // -> "This email address is already registered."
                }
                alreadyActivated = EmailAuthService.isEmailVerified(idToken);
            }
            if (!alreadyActivated) EmailAuthService.sendActivationEmail(idToken);

            try (DatabaseHelper database = new DatabaseHelper(getApplicationContext())) {
                if (!database.addLecturer(name, staffNumber, email, passwordHash, salt, alreadyActivated)) {
                    throw new IllegalStateException("This staff number or email is already registered.");
                }
            }
            return alreadyActivated;
        }, (alreadyActivated, error) -> {
            Popups.dismiss(busy);
            if (error != null) {
                String message = error instanceof IllegalStateException
                        ? error.getMessage() : EmailAuthService.friendlyMessage(error);
                Popups.showMessage(this, "Could Not Sign Up", message, this::refreshForm);
            } else if (alreadyActivated) {
                Popups.showMessage(this, "Account Created",
                        "This email was already activated, so you can log in straight away.",
                        () -> goToLogin(staffNumber));
            } else {
                Popups.showMessage(this, "Check Your Email",
                        "Your account has been created.\n\n"
                                + "We've sent an activation link to " + email + ". Open the email on this phone "
                                + "and tap the link to activate your account, then log in.\n\n"
                                + "Can't find it? Check your spam folder.",
                        () -> goToLogin(staffNumber));
            }
        });
    }

    //7. Clears every field and puts the cursor back in the first field
    private void clearForm() {
        nameInputField.setText("");
        staffNumberInputField.setText("");
        emailInputField.setText("");
        passwordInputField.setText("");
        confirmPasswordInputField.setText("");

        // Back to a fresh, untouched form (no "required" errors after a deliberate clear)
        nameTouched = staffNumberTouched = emailTouched = passwordTouched = confirmPasswordTouched = false;
        refreshForm();
        nameInputField.requestFocus();
    }

    // Back to the login page (the one already open underneath, if there is one). staffNumber may be null.
    private void goToLogin(String staffNumber) {
        Intent moveToLogin = new Intent(signUpPage.this, loginPage.class);
        moveToLogin.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        if (staffNumber != null) moveToLogin.putExtra(loginPage.EXTRA_STAFF_NUMBER, staffNumber);
        startActivity(moveToLogin);
        finish();
    }
}
