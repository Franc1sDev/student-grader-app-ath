package com.example.myfirstappath;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/*
 Sends the activation and password-reset emails using Firebase Authentication (free), through its REST API.
 No extra library is needed: plain HTTPS + JSON.

 Lecturer details (name, staff number, email, hashed password) are stored in the app's own SQLite database
 and checked there at login. Firebase is only used to SEND the emails and to CHECK the links in them.

 The emails contain links like:
     https://<project>.firebaseapp.com/__/auth/action?mode=verifyEmail&oobCode=...
     https://<project>.firebaseapp.com/__/auth/action?mode=resetPassword&oobCode=...
 Tapping one opens the app (emailLinkHandler) via an Android App Link.

 Every method here does network work: NEVER call them on the main thread (use BackgroundTask.run).
 Setup steps: see EMAIL_SETUP.md in the project root.
*/
public final class EmailAuthService {

    private EmailAuthService() { }

    private static final String BASE_URL = "https://identitytoolkit.googleapis.com/v1/accounts:";
    private static final int TIMEOUT_MS = 15_000;

    /** An error returned by Firebase, e.g. code = "EMAIL_EXISTS". */
    public static class AuthException extends Exception {
        public final String code;

        AuthException(String code) {
            super(code);
            this.code = code;
        }
    }

    /** False until firebase.apiKey and firebase.authDomain are set in local.properties. */
    public static boolean isConfigured() {
        return !BuildConfig.FIREBASE_API_KEY.isEmpty() && !BuildConfig.FIREBASE_AUTH_DOMAIN.isEmpty();
    }

    // ---------------------------------------------------------------------------------------------
    // ACCOUNTS
    // ---------------------------------------------------------------------------------------------

    /** Creates the email account. @return an ID token used to send the activation email. */
    public static String createAccount(String email, String password) throws Exception {
        JSONObject body = new JSONObject()
                .put("email", email)
                .put("password", password)
                .put("returnSecureToken", true);
        return post("signUp", body).getString("idToken");
    }

    /** Signs in with email + password. @return an ID token. */
    public static String signIn(String email, String password) throws Exception {
        JSONObject body = new JSONObject()
                .put("email", email)
                .put("password", password)
                .put("returnSecureToken", true);
        return post("signInWithPassword", body).getString("idToken");
    }

    /** True once the activation link has been opened (on the phone or in a browser). */
    public static boolean isEmailVerified(String idToken) throws Exception {
        JSONObject response = post("lookup", new JSONObject().put("idToken", idToken));
        return response.getJSONArray("users").getJSONObject(0).optBoolean("emailVerified", false);
    }

    // ---------------------------------------------------------------------------------------------
    // EMAILS
    // ---------------------------------------------------------------------------------------------

    public static void sendActivationEmail(String idToken) throws Exception {
        post("sendOobCode", new JSONObject()
                .put("requestType", "VERIFY_EMAIL")
                .put("idToken", idToken));
    }

    public static void sendPasswordResetEmail(String email) throws Exception {
        post("sendOobCode", new JSONObject()
                .put("requestType", "PASSWORD_RESET")
                .put("email", email));
    }

    // ---------------------------------------------------------------------------------------------
    // LINKS (the oobCode comes from the link in the email)
    // ---------------------------------------------------------------------------------------------

    /** Uses the code from the activation link. @return the email address that was activated. */
    public static String activateAccount(String oobCode) throws Exception {
        return post("update", new JSONObject().put("oobCode", oobCode)).getString("email");
    }

    /** Checks the code from a reset-password link without using it. @return the email it belongs to. */
    public static String checkResetCode(String oobCode) throws Exception {
        return post("resetPassword", new JSONObject().put("oobCode", oobCode)).getString("email");
    }

    /** Uses the code from a reset-password link to set the new password. @return the email it belongs to. */
    public static String resetPassword(String oobCode, String newPassword) throws Exception {
        return post("resetPassword", new JSONObject()
                .put("oobCode", oobCode)
                .put("newPassword", newPassword)).getString("email");
    }

    // ---------------------------------------------------------------------------------------------

    /** A message a lecturer can understand, for any error thrown by the methods above. */
    public static String friendlyMessage(Exception error) {
        if (error instanceof IOException) {
            return "Could not reach the email service. Check your internet connection and try again.";
        }
        if (!(error instanceof AuthException)) {
            return "Something went wrong: " + error.getMessage();
        }
        String code = ((AuthException) error).code;
        switch (code) {
            case "EMAIL_EXISTS":
                return "This email address is already registered.";
            case "EMAIL_NOT_FOUND":
            case "INVALID_PASSWORD":
            case "INVALID_LOGIN_CREDENTIALS":
                return "The email service did not accept this email and password.";
            case "INVALID_OOB_CODE":
                return "This link is not valid or has already been used.";
            case "EXPIRED_OOB_CODE":
                return "This link has expired. Please request a new one.";
            case "TOO_MANY_ATTEMPTS_TRY_LATER":
                return "Too many attempts. Please wait a few minutes and try again.";
            case "USER_DISABLED":
                return "This account has been disabled.";
            case "OPERATION_NOT_ALLOWED":
                return "Email/Password sign-in is not enabled in the Firebase project (see EMAIL_SETUP.md).";
            case "WEAK_PASSWORD":
                return "The password is too weak.";
            default:
                if (code.startsWith("API key not valid") || code.startsWith("API_KEY")) {
                    return "The email service API key is not valid (check local.properties).";
                }
                return "Email service error: " + code;
        }
    }

    // ---------------------------------------------------------------------------------------------

    private static JSONObject post(String method, JSONObject body) throws IOException, JSONException, AuthException {
        if (!isConfigured()) throw new AuthException("NOT_CONFIGURED");

        URL url = new URL(BASE_URL + method + "?key=" + BuildConfig.FIREBASE_API_KEY);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        try {
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(TIMEOUT_MS);
            connection.setReadTimeout(TIMEOUT_MS);
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            connection.setRequestProperty("X-Android-Package", BuildConfig.APPLICATION_ID);
            connection.setRequestProperty("X-Firebase-Locale", "en"); // language of the emails

            try (OutputStream out = connection.getOutputStream()) {
                out.write(body.toString().getBytes(StandardCharsets.UTF_8));
            }

            int status = connection.getResponseCode();
            InputStream stream = status < 400 ? connection.getInputStream() : connection.getErrorStream();
            String text = stream == null ? "{}" : readAll(stream);
            JSONObject response = new JSONObject(text);

            if (status >= 400) {
                JSONObject error = response.optJSONObject("error");
                String message = error == null ? "HTTP_" + status : error.optString("message", "HTTP_" + status);
                // Firebase sometimes adds details: "WEAK_PASSWORD : Password should be at least 6 characters"
                int detail = message.indexOf(" : ");
                throw new AuthException(detail > 0 ? message.substring(0, detail).trim() : message.trim());
            }
            return response;
        } finally {
            connection.disconnect();
        }
    }

    private static String readAll(InputStream in) throws IOException {
        try (InputStream stream = in; ByteArrayOutputStream buffer = new ByteArrayOutputStream()) {
            byte[] chunk = new byte[4096];
            int read;
            while ((read = stream.read(chunk)) != -1) buffer.write(chunk, 0, read);
            return buffer.toString(StandardCharsets.UTF_8.name());
        }
    }
}
