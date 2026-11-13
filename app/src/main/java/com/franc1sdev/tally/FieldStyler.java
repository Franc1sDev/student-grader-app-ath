package com.franc1sdev.tally;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Paint;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.textfield.TextInputLayout;

/*
 The same look for every input field and main button in the app:

   NEUTRAL: no alerts (field not used yet, or blocked)
   RED:     red text, red outline and a red message under the field
   GREEN:   green text, green outline, a green message under the field and a green tick icon

   Main buttons: grey + disabled until every detail is correct, then green + clickable.
*/
public class FieldStyler {

    private final int successGreen, errorRed, normalTextColor, disabledGrey;
    private final int focusedOutlineColor, unfocusedOutlineColor;

    /** @param anyField any field on the page, used to read the theme's normal colours (call before styling anything). */
    public FieldStyler(Context context, TextInputLayout anyField) {
        successGreen = ContextCompat.getColor(context, R.color.success_green);
        errorRed = ContextCompat.getColor(context, R.color.error_red);
        disabledGrey = ContextCompat.getColor(context, R.color.material_dynamic_neutral80);
        normalTextColor = MaterialColors.getColor(anyField, com.google.android.material.R.attr.colorOnSurface);
        focusedOutlineColor = anyField.getBoxStrokeColor(); // the boxStrokeColor set in XML
        unfocusedOutlineColor = MaterialColors.getColor(anyField, com.google.android.material.R.attr.colorOutline);
    }

    // RED
    public void showError(TextInputLayout field, String message) {
        clearSuccess(field);
        field.setError(message);
        setTypedTextColor(field, errorRed);
    }

    // NEUTRAL
    public void showNeutral(TextInputLayout field) {
        clearSuccess(field);
        field.setError(null);
        setTypedTextColor(field, normalTextColor);
    }

    // NEUTRAL with a plain grey message under the field (e.g. "Checking...")
    public void showInfo(TextInputLayout field, String message) {
        showNeutral(field);
        field.setHelperText(message);
        field.setHelperTextColor(ColorStateList.valueOf(unfocusedOutlineColor));
    }

    // GREEN
    public void showSuccess(TextInputLayout field, String message) {
        field.setError(null);
        setTypedTextColor(field, successGreen);
        field.setHelperText(message);
        field.setHelperTextColor(ColorStateList.valueOf(successGreen));
        field.setBoxStrokeColorStateList(outlineColors(successGreen, successGreen));

        if (endIconInUse(field)) {
            // The end (right) side holds the show/hide eye or the dropdown arrow, so the tick goes at the start
            field.setStartIconDrawable(R.drawable.ic_check_circle);
            field.setStartIconTintList(ColorStateList.valueOf(successGreen));
            field.setStartIconContentDescription(message);
        } else {
            field.setEndIconMode(TextInputLayout.END_ICON_CUSTOM);
            field.setEndIconDrawable(R.drawable.ic_check_circle);
            field.setEndIconTintList(ColorStateList.valueOf(successGreen));
            field.setEndIconContentDescription(message);
        }
    }

    // Blocked = greyed out, can't be typed in
    public void setBlocked(TextInputLayout field, boolean blocked) {
        if (field.isEnabled() == !blocked) return;
        field.setEnabled(!blocked);
        if (field.getEditText() != null) field.getEditText().setEnabled(!blocked);
    }

    // Main button: grey + disabled, or green + clickable
    public void setButtonReady(MaterialButton button, boolean ready) {
        button.setEnabled(ready);
        button.setBackgroundTintList(ColorStateList.valueOf(ready ? successGreen : disabledGrey));
    }

    // Removes the green state (tick, green outline, green message) from a field
    private void clearSuccess(TextInputLayout field) {
        field.setHelperText(null);
        field.setBoxStrokeColorStateList(outlineColors(focusedOutlineColor, unfocusedOutlineColor));
        if (endIconInUse(field)) {
            field.setStartIconDrawable(null);
        } else {
            field.setEndIconMode(TextInputLayout.END_ICON_NONE);
        }
    }

    private static boolean endIconInUse(TextInputLayout field) {
        int mode = field.getEndIconMode();
        return mode == TextInputLayout.END_ICON_PASSWORD_TOGGLE || mode == TextInputLayout.END_ICON_DROPDOWN_MENU;
    }

    private static void setTypedTextColor(TextInputLayout field, int color) {
        if (field.getEditText() != null) field.getEditText().setTextColor(color);
    }

    // Outline colour for when the field is focused (being typed in) vs not focused
    private static ColorStateList outlineColors(int focused, int unfocused) {
        return new ColorStateList(
                new int[][]{ new int[]{android.R.attr.state_focused}, new int[]{} },
                new int[]{ focused, unfocused });
    }

    // ---------------------------------------------------------------------------------------------

    /** Real-time checking: runs the action every time the text of a field changes. */
    public static TextWatcher onTextChanged(Runnable action) {
        return new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { }
            @Override public void afterTextChanged(Editable s) { action.run(); }
        };
    }

    /** Underlines a text link such as "Forgot password?". */
    public static void underline(TextView link) {
        link.setPaintFlags(link.getPaintFlags() | Paint.UNDERLINE_TEXT_FLAG);
    }
}
