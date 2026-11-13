package com.franc1sdev.tally;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

/*
 Runs when the lecturer taps a link in one of the app's emails, e.g.
     https://<project>.firebaseapp.com/__/auth/action?mode=verifyEmail&oobCode=ABC...
 It has no screen: it reads the link and opens the right page, then closes.
     mode=verifyEmail   -> login page, which activates the account
     mode=resetPassword -> reset password page
*/
public class emailLinkHandler extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Uri link = getIntent().getData();
        String mode = link == null ? null : link.getQueryParameter("mode");
        String code = link == null ? null : link.getQueryParameter("oobCode");

        // Some email apps wrap the real link inside a "link" parameter
        if (mode == null && link != null && link.getQueryParameter("link") != null) {
            Uri inner = Uri.parse(link.getQueryParameter("link"));
            mode = inner.getQueryParameter("mode");
            code = inner.getQueryParameter("oobCode");
        }

        Intent next;
        if ("resetPassword".equals(mode) && code != null) {
            next = new Intent(this, resetPasswordPage.class);
            next.putExtra(resetPasswordPage.EXTRA_RESET_CODE, code);
        } else {
            next = new Intent(this, loginPage.class);
            if ("verifyEmail".equals(mode) && code != null) next.putExtra(loginPage.EXTRA_ACTIVATION_CODE, code);
        }
        // Start the app fresh from this page
        next.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(next);
        finish(); // must finish before onResume (Theme.NoDisplay)
    }
}
