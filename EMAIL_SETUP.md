# Email setup (activation and password-reset links)

The app sends the **activation email** (on sign up) and the **password reset email** (from "Forgot password?")
through **Firebase Authentication**. It's free on the Spark plan and needs no server of your own.

Lecturer details (name, staff number, email and a *hashed* password) are stored in the app's own SQLite
database (`lecturers` table) and are checked there at login. Firebase only **sends** the emails and
**checks** the links in them.

Until you do the setup below:
- **Debug builds** still let you sign up, but no email is sent and the account is activated right away
  (a "Test Mode" pop up says so).
- **Release builds** refuse to sign up.

---

## 1. Create the Firebase project (one time)

1. Go to <https://console.firebase.google.com> → **Add project** (Google Analytics is not needed).
2. **Build → Authentication → Get started → Sign-in method → Email/Password → Enable → Save.**
3. **Project settings (gear icon) → General**. Write down:
   - **Web API Key**, e.g. `AIzaSy...`
   - **Project ID**, e.g. `student-grader-1234`. Your link domain is then `student-grader-1234.firebaseapp.com`.

## 2. Tell the app about it

Add these two lines to `local.properties` in the project root. That file is not committed to git.

```properties
firebase.apiKey=AIzaSy...your key...
firebase.authDomain=student-grader-1234.firebaseapp.com
```

Then **Sync Gradle** and rebuild/reinstall the app.

## 3. Make the email links open the app (Android App Links)

The links in the emails look like
`https://student-grader-1234.firebaseapp.com/__/auth/action?mode=verifyEmail&oobCode=...`.
Android only opens them in the app, rather than the browser, once the domain is verified for the app:

1. Get your signing certificate's **SHA-256**: in Android Studio's terminal run `./gradlew signingReport`
   and copy the `SHA-256` of the `debug` variant (and the release one when you publish).
2. Firebase console → **Project settings → General → Your apps → Add app → Android**:
   - Package name: `com.example.myfirstappath`
   - Add the **SHA-256** fingerprint(s) (*Add fingerprint*).
3. Check that `https://<your-project-id>.firebaseapp.com/.well-known/assetlinks.json` lists
   `com.example.myfirstappath` with your SHA-256. Then reinstall the app so Android re-verifies the domain.
   On a connected phone you can check with:
   `adb shell pm get-app-links com.example.myfirstappath` (the domain should say `verified`).

**Quick option for testing:** on the phone, open **Settings → Apps → myfirstApp(ATH) → Open by default →
Add link** and tick your `firebaseapp.com` domain. Or run:

```
adb shell pm set-app-links-user-selection --user cur --package com.example.myfirstappath true student-grader-1234.firebaseapp.com
```

### If a link opens in the browser anyway

It still works, just not as smoothly:
- **Activation:** the Firebase web page activates the account. When the lecturer logs in, the app asks
  Firebase, sees the account is activated, and remembers it.
- **Password reset:** the Firebase web page lets them choose a new password. At login the app sees the
  typed password doesn't match the old one, checks it with Firebase ("Checking your new password..."),
  and saves it in the database.

## 4. Optional: nicer emails

**Authentication → Templates** lets you edit the sender name, the subject (e.g. *"Activate your Student
Grader account"*) and the text of the *Email address verification* and *Password reset* emails.
**Don't change the action URL.** The app listens for the default `/__/auth/action` link.

Firebase emails sometimes land in **spam** at first. Ask lecturers to check there.

## How the flows work

| Step | What happens |
|---|---|
| Sign up | Fields checked live → Sign Up turns green → confirm → Firebase account created + activation email sent → details saved in SQLite (not activated) → back to login with the staff number filled in |
| Tap activation link | App opens → account activated (Firebase + SQLite) → login page with the staff number filled in |
| Log in before activating | Details checked in SQLite → app asks Firebase → "Account Not Activated" pop up with **Resend Link** |
| Forgot password? | Pop up: registered email turns green → reset email sent |
| Tap reset link | App opens the **Reset Password** page → new password typed twice (live checks) → saved in Firebase + SQLite → login |
