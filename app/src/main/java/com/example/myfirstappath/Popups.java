package com.example.myfirstappath;

import android.app.Activity;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.CircularProgressIndicator;

/** Pop ups shared by the login, sign up and reset password pages. */
public final class Popups {

    private Popups() { }

    /** A spinner with a message that can't be closed by the user. Call dismiss() on it when the work is done. */
    public static AlertDialog showBusy(Activity page, String message) {
        int padding = dp(page, 24);

        LinearLayout content = new LinearLayout(page);
        content.setOrientation(LinearLayout.HORIZONTAL);
        content.setGravity(Gravity.CENTER_VERTICAL);
        content.setPadding(padding, padding, padding, padding);

        CircularProgressIndicator spinner = new CircularProgressIndicator(page);
        spinner.setIndeterminate(true);
        content.addView(spinner);

        TextView text = new TextView(page);
        text.setText(message);
        text.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        text.setPadding(padding, 0, 0, 0);
        content.addView(text);

        return new MaterialAlertDialogBuilder(page)
                .setView(content)
                .setCancelable(false)
                .show();
    }

    /** A message with a single OK button. onOk may be null. */
    public static void showMessage(Activity page, String title, String message, Runnable onOk) {
        new MaterialAlertDialogBuilder(page)
                .setTitle(title)
                .setMessage(message)
                .setCancelable(false)
                .setPositiveButton("OK", (dialog, which) -> {
                    if (onOk != null) onOk.run();
                })
                .show();
    }

    public static void dismiss(AlertDialog dialog) {
        if (dialog != null && dialog.isShowing()) dialog.dismiss();
    }

    private static int dp(Activity page, int value) {
        return Math.round(value * page.getResources().getDisplayMetrics().density);
    }
}
