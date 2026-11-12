package com.example.myfirstappath;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
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

import java.io.IOException;

public class loginPage extends AppCompatActivity {

    // Extras other pages can send to this page:
    public static final String EXTRA_STAFF_NUMBER = "staffNumber";        // fills in the staff number
    public static final String EXTRA_ACTIVATION_CODE = "activationCode";  // from the activation link in the email

    // Minimum password length the email service accepts (used to check a password changed in a browser)
    private static final int SERVICE_MIN_PASSWORD_LENGTH = 6;
    private static final long NEW_PASSWORD_CHECK_DELAY_MS = 1000;

    //1. Declare the views (UI components) for whom the logic applies
    TextInputLayout staffNumberLayout, passwordLayout;   // the outlined boxes (they show the red/green alerts)
    EditText staffNumberInputField, passwordInputField;  // the actual typing areas inside the boxes
    MaterialButton login, clearFields;
    TextView forgotPassword, signUpLink;

    DatabaseHelper db;
    FieldStyler styler;

    // The lecturer whose staff number is typed in (null while the staff number is not recognised)
    Lecturer lecturer;

    // Live state of each field:
    boolean staffNumberCorrect = false, passwordCorrect = false;
    // "Touched" = the user has typed something in the field at least once.
    // We only show "required" errors on fields the user has already interacted with.
    boolean staffNumberTouched = false, passwordTouched = false;

    // If the password was reset in a web browser (instead of in the app), the new password is checked
    // with the email service once the lecturer stops typing, and then saved in the database.
    final Handler handler = new Handler(Looper.getMainLooper());
    Runnable newPasswordCheck;
    String rejectedPassword, rejectedPasswordMessage;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.login_page);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            // systemBars + ime: when the keypad opens, the page shrinks above it and becomes scrollable,
            // so the keypad never covers a field or button
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.ime());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        db = new DatabaseHelper(this);

        //2. Tie the declared views to the actual IDs of same views that you set in XML:

        staffNumberLayout = findViewById(R.id.til_staffNumber);
        passwordLayout = findViewById(R.id.til_Password);
        staffNumberInputField = findViewById(R.id.edtxt_staffNumber);
        passwordInputField = findViewById(R.id.edtxt_Password);
        login = findViewById(R.id.btn_login);
        clearFields = findViewById(R.id.btn_ClearFields);
        forgotPassword = findViewById(R.id.txtvw_forgotPassword);
        signUpLink = findViewById(R.id.txtvw_signUpLink);

        styler = new FieldStyler(this, staffNumberLayout); // loads the alert colours once
        FieldStyler.underline(forgotPassword);
        FieldStyler.underline(signUpLink);

        //3. Write code for behavior of those declared views:

            //3a. Real-time checking: re-validate the form every time a field's text changes
            // (the password depends on the staff number, so both are re-checked together)
        staffNumberInputField.addTextChangedListener(FieldStyler.onTextChanged(() -> {
            if (staffNumberInputField.length() > 0) staffNumberTouched = true;
            refreshForm();
        }));

        passwordInputField.addTextChangedListener(FieldStyler.onTextChanged(() -> {
            if (passwordInputField.length() > 0) passwordTouched = true;
            refreshForm();
        }));

            //3b. Functionality/Logic for X button (clearFields):
        clearFields.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                staffNumberInputField.setText("");
                passwordInputField.setText("");
                // Back to a fresh, untouched form (no "required" errors after a deliberate clear)
                staffNumberTouched = false;
                passwordTouched = false;
                refreshForm();
                staffNumberInputField.requestFocus();
            }
        });

            //3c. Functionality/Logic for Login button:
            // It can only be clicked once both fields are correct.
        login.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                refreshForm();
                if (!staffNumberCorrect || !passwordCorrect || lecturer == null) return; // safety check

                if (lecturer.activated) {
                    goToDashboard();
                } else {
                    // Not activated yet on this phone: ask the email service if the activation link was opened
                    checkActivationThenLogin(lecturer, passwordInputField.getText().toString());
                }
            }
        });

            //3d. "Forgot password?" opens a pop up that emails a reset link
        forgotPassword.setOnClickListener(view -> showForgotPasswordPopup());

            //3e. "I don't have an account. Sign me up" opens the sign up page
        signUpLink.setOnClickListener(view -> startActivity(new Intent(loginPage.this, signUpPage.class)));

        // Set the starting look of the form (neutral fields, disabled grey login button):
        refreshForm();

        // Opened from an activation link, or from the sign up / reset password page?
        // (Only the first time: not again when the phone is rotated.)
        if (savedInstanceState == null) handleIncomingIntent(getIntent());
    }

    // This page is "singleTop": if it is already open, other pages reach it here instead of onCreate
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIncomingIntent(intent);
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        db.close();
        super.onDestroy();
    }

    private void handleIncomingIntent(Intent intent) {
        if (intent == null) return;

        String staffNumber = intent.getStringExtra(EXTRA_STAFF_NUMBER);
        if (staffNumber != null) fillStaffNumber(staffNumber);

        String activationCode = intent.getStringExtra(EXTRA_ACTIVATION_CODE);
        if (activationCode != null) activateAccount(activationCode);
    }

    // Puts the staff number in and moves the cursor to the (empty) password field
    private void fillStaffNumber(String staffNumber) {
        staffNumberInputField.setText(staffNumber);
        rejectedPassword = null;
        passwordInputField.setText("");
        passwordTouched = false;
        refreshForm();
        passwordInputField.requestFocus();
    }

    //4. Validation for each field (checked against the lecturers saved in the database):

    private void refreshForm() {
        validateStaffNumber();
        validatePassword();
        updateLoginButton();
    }

    private void validateStaffNumber() {
        String typed = AccountRules.normaliseStaffNumber(staffNumberInputField.getText().toString());
        lecturer = typed.isEmpty() ? null : db.getLecturerByStaffNumber(typed);
        staffNumberCorrect = lecturer != null;

        if (typed.isEmpty()) {
            if (staffNumberTouched) styler.showError(staffNumberLayout, "Staff number is required");
            else styler.showNeutral(staffNumberLayout);
        } else if (staffNumberCorrect) {
            styler.showSuccess(staffNumberLayout, "Staff number verified");
        } else {
            styler.showError(staffNumberLayout, "Staff number not recognised");
        }
    }

    private void validatePassword() {
        if (newPasswordCheck != null) handler.removeCallbacks(newPasswordCheck);

        String typed = passwordInputField.getText().toString();
        passwordCorrect = lecturer != null && lecturer.passwordMatches(typed);

        if (typed.isEmpty()) {
            if (passwordTouched) styler.showError(passwordLayout, "Password is required");
            else styler.showNeutral(passwordLayout);
        } else if (passwordCorrect) {
            styler.showSuccess(passwordLayout, "Password correct");
        } else if (lecturer == null) {
            styler.showNeutral(passwordLayout); // the staff number field already shows what is wrong
        } else if (typed.equals(rejectedPassword)) {
            styler.showError(passwordLayout, rejectedPasswordMessage);
        } else if (mayBeNewPasswordFromBrowser(typed)) {
            styler.showInfo(passwordLayout, "Checking your new password...");
            scheduleNewPasswordCheck(lecturer, typed);
        } else {
            styler.showError(passwordLayout, "Incorrect password");
        }
    }

    //5. Login button: grey + disabled until BOTH fields are correct, then green + clickable.
    private void updateLoginButton() {
        styler.setButtonReady(login, staffNumberCorrect && passwordCorrect);
    }

    private void goToDashboard() {
        Intent moveToDashboard = new Intent(loginPage.this, dashboard.class);
        startActivity(moveToDashboard); //navigation actually happens through this line
        finish(); // closes the login page, so Back from the dashboard can't return to it
    }

    //6. Account activation

    // Opened from the activation link in the email (see emailLinkHandler)
    private void activateAccount(String activationCode) {
        if (!EmailAuthService.isConfigured()) {
            Popups.showMessage(this, "Email Service Not Set Up", notConfiguredMessage(), null);
            return;
        }
        AlertDialog busy = Popups.showBusy(this, "Activating your account...");
        BackgroundTask.run(this, () -> {
            String email = EmailAuthService.activateAccount(activationCode);
            try (DatabaseHelper database = new DatabaseHelper(getApplicationContext())) {
                Lecturer activated = database.getLecturerByEmail(email);
                if (activated != null) database.setActivated(email);
                return activated;
            }
        }, (activated, error) -> {
            Popups.dismiss(busy);
            if (error != null) {
                Popups.showMessage(this, "Activation Failed", EmailAuthService.friendlyMessage(error)
                        + "\n\nIf you have already activated your account, just log in.", null);
            } else if (activated == null) {
                Popups.showMessage(this, "Email Verified",
                        "Your email was verified, but no lecturer account on this phone uses it. Please sign up first.", null);
            } else {
                fillStaffNumber(activated.staffNumber);
                Popups.showMessage(this, "Account Activated",
                        "Welcome, " + activated.name + "! Your account is now active.\n\nEnter your password to log in.", null);
            }
        });
    }

    // The account isn't marked active on this phone: maybe the link was opened in a browser, so ask the service
    private void checkActivationThenLogin(Lecturer account, String password) {
        if (!EmailAuthService.isConfigured()) {
            Popups.showMessage(this, "Account Not Activated",
                    "This account has not been activated yet.\n\n" + notConfiguredMessage(), null);
            return;
        }
        AlertDialog busy = Popups.showBusy(this, "Checking your account...");
        BackgroundTask.run(this, () -> {
            String idToken = EmailAuthService.signIn(account.email, password);
            boolean verified = EmailAuthService.isEmailVerified(idToken);
            if (verified) {
                try (DatabaseHelper database = new DatabaseHelper(getApplicationContext())) {
                    database.setActivated(account.email);
                }
            }
            return verified ? null : idToken; // null = activated; otherwise the token needed to resend the link
        }, (idTokenIfNotActivated, error) -> {
            Popups.dismiss(busy);
            if (error != null) {
                Popups.showMessage(this, "Could Not Check Your Account", EmailAuthService.friendlyMessage(error), null);
            } else if (idTokenIfNotActivated == null) {
                goToDashboard();
            } else {
                showNotActivatedPopup(account.email, idTokenIfNotActivated);
            }
        });
    }

    private void showNotActivatedPopup(String email, String idToken) {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Account Not Activated")
                .setMessage("We sent an activation link to " + email + " when you signed up.\n\n"
                        + "Open that email on this phone and tap the link to activate your account, then log in.\n\n"
                        + "Can't find it? Check your spam folder, or send a new link.")
                .setNegativeButton("OK", (dialog, which) -> dialog.dismiss())
                .setPositiveButton("Resend Link", (dialog, which) -> resendActivationLink(email, idToken))
                .show();
    }

    private void resendActivationLink(String email, String idToken) {
        AlertDialog busy = Popups.showBusy(this, "Sending activation link...");
        BackgroundTask.run(this, () -> {
            EmailAuthService.sendActivationEmail(idToken);
            return true;
        }, (sent, error) -> {
            Popups.dismiss(busy);
            if (error != null) {
                Popups.showMessage(this, "Could Not Send Link", EmailAuthService.friendlyMessage(error), null);
            } else {
                Popups.showMessage(this, "Activation Link Sent", "A new activation link was sent to " + email + ".", null);
            }
        });
    }

    //7. Forgot password: a pop up with an email field (same live checks and button colours as the pages)

    private void showForgotPasswordPopup() {
        View content = getLayoutInflater().inflate(R.layout.dialog_forgot_password, null);
        TextInputLayout emailLayout = content.findViewById(R.id.til_resetEmail);
        EditText emailInputField = content.findViewById(R.id.edtxt_resetEmail);
        MaterialButton sendLink = content.findViewById(R.id.btn_sendResetLink);
        MaterialButton cancel = content.findViewById(R.id.btn_cancelReset);
        FieldStyler popupStyler = new FieldStyler(this, emailLayout);

        AlertDialog popup = new MaterialAlertDialogBuilder(this)
                .setTitle("Forgot Password")
                .setView(content)
                .create();

        final boolean[] emailTouched = {false};
        Runnable validateEmail = () -> {
            String typed = AccountRules.normaliseEmail(emailInputField.getText().toString());
            boolean correct = false;
            if (typed.isEmpty()) {
                if (emailTouched[0]) popupStyler.showError(emailLayout, "Email is required");
                else popupStyler.showNeutral(emailLayout);
            } else if (!AccountRules.isValidEmail(typed)) {
                popupStyler.showError(emailLayout, "Enter a valid email address");
            } else if (!db.emailExists(typed)) {
                popupStyler.showError(emailLayout, "No account uses this email");
            } else {
                correct = true;
                popupStyler.showSuccess(emailLayout, "Registered email");
            }
            popupStyler.setButtonReady(sendLink, correct);
        };

        emailInputField.addTextChangedListener(FieldStyler.onTextChanged(() -> {
            if (emailInputField.length() > 0) emailTouched[0] = true;
            validateEmail.run();
        }));
        validateEmail.run();
        // Staff number already verified? Fill in that lecturer's email for them
        if (lecturer != null) emailInputField.setText(lecturer.email);

        cancel.setOnClickListener(view -> popup.dismiss());

        sendLink.setOnClickListener(view -> {
            validateEmail.run();
            if (!sendLink.isEnabled()) return; // safety check
            String email = AccountRules.normaliseEmail(emailInputField.getText().toString());

            if (!EmailAuthService.isConfigured()) {
                Popups.showMessage(this, "Email Service Not Set Up", notConfiguredMessage(), null);
                return;
            }
            AlertDialog busy = Popups.showBusy(this, "Sending reset link...");
            BackgroundTask.run(this, () -> {
                EmailAuthService.sendPasswordResetEmail(email);
                try (DatabaseHelper database = new DatabaseHelper(getApplicationContext())) {
                    database.setPendingReset(email, true);
                }
                return true;
            }, (sent, error) -> {
                Popups.dismiss(busy);
                if (error != null) {
                    Popups.showMessage(this, "Could Not Send Link", EmailAuthService.friendlyMessage(error), null);
                    return;
                }
                popup.dismiss();
                rejectedPassword = null; // a new reset is pending, so check typed passwords again
                refreshForm();
                Popups.showMessage(this, "Reset Link Sent",
                        "We've sent a password reset link to " + email + ".\n\n"
                                + "Open the email on this phone and tap the link to choose a new password, then log in.\n\n"
                                + "Can't find it? Check your spam folder.", null);
            });
        });

        popup.show();
    }

    //8. Password reset in a web browser instead of in the app: the database still has the old password,
    //   so (only while a reset is pending) a "wrong" password is checked with the email service.

    private boolean mayBeNewPasswordFromBrowser(String typed) {
        return lecturer != null && lecturer.pendingReset && EmailAuthService.isConfigured()
                && typed.length() >= SERVICE_MIN_PASSWORD_LENGTH;
    }

    private void scheduleNewPasswordCheck(Lecturer account, String typed) {
        newPasswordCheck = () -> BackgroundTask.run(this, () -> {
            EmailAuthService.signIn(account.email, typed); // throws if the service rejects the password
            String salt = PasswordHasher.newSalt();
            try (DatabaseHelper database = new DatabaseHelper(getApplicationContext())) {
                database.updatePassword(account.email, PasswordHasher.hash(typed, salt), salt);
            }
            return true;
        }, (accepted, error) -> {
            if (error != null) {
                rejectedPassword = typed;
                rejectedPasswordMessage = error instanceof IOException
                        ? "Incorrect password (offline: couldn't check for a new password)"
                        : "Incorrect password";
            }
            refreshForm(); // reloads the lecturer, so an accepted new password now shows green
        });
        handler.postDelayed(newPasswordCheck, NEW_PASSWORD_CHECK_DELAY_MS);
    }

    static String notConfiguredMessage() {
        return "The email service is not set up on this copy of the app, so emails can't be sent or checked. "
                + "Add the Firebase settings to local.properties (see EMAIL_SETUP.md) and rebuild the app.";
    }
}
