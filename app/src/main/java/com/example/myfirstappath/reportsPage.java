package com.example.myfirstappath;

import android.Manifest;
import android.content.ActivityNotFoundException;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.pdf.PdfDocument;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.text.TextPaint;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class reportsPage extends AppCompatActivity {

    // Grid columns (the same font size is used in portrait and landscape)
    private static final String[] COLUMN_TITLES = {"Admno", "Name", "Trimester", "Average", "Grade", "Remark"};
    // Share of the screen width each column gets, so the grid always fits the screen width
    private static final float[] COLUMN_WEIGHTS = {1.15f, 1.6f, 1.25f, 1.15f, 1.0f, 1.1f};
    private static final float GRID_TEXT_SIZE_SP = 13f;

    private static final int COL_ADMNO = 0, COL_NAME = 1, COL_TRIMESTER = 2, COL_AVERAGE = 3, COL_GRADE = 4, COL_REMARK = 5;

    private static final String ALL_TRIMESTERS = "All Trimesters";

    private static final String STATE_SORT_COLUMN = "sortColumn";
    private static final String STATE_SORT_ASCENDING = "sortAscending";
    private static final String STATE_TRIMESTER_FILTER = "trimesterFilter";

    LinearLayout headerRow, rowsContainer;
    TextView noRecords;
    MaterialButton downloadReport;
    MaterialAutoCompleteTextView trimesterFilter;
    TextView[][] sortArrows = new TextView[COLUMN_TITLES.length][2]; // [column][0 = up (ascending), 1 = down (descending)]

    List<StudentResult> allResults = new ArrayList<>(); // every student, every trimester
    List<StudentResult> students = new ArrayList<>();   // the rows shown (filtered by trimester)
    String selectedTrimester = ALL_TRIMESTERS;
    int sortColumn = COL_ADMNO;     // default: admno, smallest to largest
    boolean sortAscending = true;

    int successGreen, errorRed, textColor, stripeColor;

    // Only needed on Android 9 (API 28): permission to save into the Downloads folder
    private final ActivityResultLauncher<String> storagePermission =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) downloadPdf();
                else Toast.makeText(this, "Storage permission is needed to save the PDF.", Toast.LENGTH_LONG).show();
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.reports_page);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.ime());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });

        headerRow = findViewById(R.id.ll_reportHeader);
        rowsContainer = findViewById(R.id.ll_reportRows);
        noRecords = findViewById(R.id.txtvw_noRecords);
        downloadReport = findViewById(R.id.btn_generatePDF);
        trimesterFilter = findViewById(R.id.actxt_reportTrimester);

        successGreen = ContextCompat.getColor(this, R.color.success_green);
        errorRed = ContextCompat.getColor(this, R.color.error_red);
        textColor = MaterialColors.getColor(rowsContainer, com.google.android.material.R.attr.colorOnSurface);
        stripeColor = ColorUtils.setAlphaComponent(textColor, 20);

        // Keep the chosen sort when the phone is rotated
        if (savedInstanceState != null) {
            sortColumn = savedInstanceState.getInt(STATE_SORT_COLUMN, COL_ADMNO);
            sortAscending = savedInstanceState.getBoolean(STATE_SORT_ASCENDING, true);
            selectedTrimester = savedInstanceState.getString(STATE_TRIMESTER_FILTER, ALL_TRIMESTERS);
        }

        buildHeader();

        DatabaseHelper db = new DatabaseHelper(this);
        allResults = db.getAllResults();
        List<String> trimesters = db.getTrimestersInUse(); // newest first
        db.close();

        // Trimester filter: "All Trimesters" + every trimester that has results
        List<String> filterOptions = new ArrayList<>();
        filterOptions.add(ALL_TRIMESTERS);
        filterOptions.addAll(trimesters);
        if (!filterOptions.contains(selectedTrimester)) selectedTrimester = ALL_TRIMESTERS;
        trimesterFilter.setSimpleItems(filterOptions.toArray(new String[0]));
        trimesterFilter.setText(selectedTrimester, false);
        trimesterFilter.setOnItemClickListener((parent, view, position, id) -> {
            selectedTrimester = filterOptions.get(position);
            applyTrimesterFilter();
        });

        applyTrimesterFilter();

        downloadReport.setOnClickListener(view -> {
            if (students.isEmpty()) {
                Toast.makeText(this, "No student records to download.", Toast.LENGTH_LONG).show();
                return;
            }
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q
                    && ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                storagePermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE);
            } else {
                downloadPdf();
            }
        });
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(STATE_SORT_COLUMN, sortColumn);
        outState.putBoolean(STATE_SORT_ASCENDING, sortAscending);
        outState.putString(STATE_TRIMESTER_FILTER, selectedTrimester);
    }

    // Shows only the results of the chosen trimester (or all of them)
    private void applyTrimesterFilter() {
        students = new ArrayList<>();
        for (StudentResult s : allResults) {
            if (ALL_TRIMESTERS.equals(selectedTrimester) || selectedTrimester.equals(s.trimester)) students.add(s);
        }
        noRecords.setText(allResults.isEmpty() ? "No student records yet." : "No student records for " + selectedTrimester + ".");
        showRows();
    }

    // ---------------------------------------------------------------------------------------------
    // GRID
    // ---------------------------------------------------------------------------------------------

    // Header: each column has its title and a ▲ (ascending) and ▼ (descending) sort button under it
    private void buildHeader() {
        headerRow.removeAllViews();
        for (int col = 0; col < COLUMN_TITLES.length; col++) {
            LinearLayout cell = new LinearLayout(this);
            cell.setOrientation(LinearLayout.VERTICAL);
            cell.setGravity(Gravity.CENTER_HORIZONTAL);
            cell.setPadding(dp(2), dp(6), dp(2), dp(2));
            cell.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, COLUMN_WEIGHTS[col]));

            TextView title = gridText(COLUMN_TITLES[col], Color.WHITE, true);
            cell.addView(title);

            LinearLayout arrows = new LinearLayout(this);
            arrows.setOrientation(LinearLayout.HORIZONTAL);
            arrows.setGravity(Gravity.CENTER);
            sortArrows[col][0] = sortArrow("▲", col, true);
            sortArrows[col][1] = sortArrow("▼", col, false);
            arrows.addView(sortArrows[col][0]);
            arrows.addView(sortArrows[col][1]);
            cell.addView(arrows);

            headerRow.addView(cell);
        }
        highlightActiveArrow();
    }

    private TextView sortArrow(String symbol, int column, boolean ascending) {
        TextView arrow = gridText(symbol, Color.WHITE, true);
        arrow.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        arrow.setPadding(dp(5), dp(4), dp(5), dp(4));
        arrow.setContentDescription("Sort " + COLUMN_TITLES[column] + (ascending ? " ascending" : " descending"));
        arrow.setClickable(true);
        arrow.setFocusable(true);
        arrow.setOnClickListener(v -> {
            sortColumn = column;
            sortAscending = ascending;
            highlightActiveArrow();
            showRows();
        });
        return arrow;
    }

    // The sort currently in use is bright white; the others are faded
    private void highlightActiveArrow() {
        for (int col = 0; col < sortArrows.length; col++) {
            for (int dir = 0; dir < 2; dir++) {
                boolean active = col == sortColumn && (dir == 0) == sortAscending;
                sortArrows[col][dir].setAlpha(active ? 1f : 0.4f);
            }
        }
    }

    private void showRows() {
        rowsContainer.removeAllViews();
        noRecords.setVisibility(students.isEmpty() ? View.VISIBLE : View.GONE);

        List<StudentResult> sorted = new ArrayList<>(students);
        Collections.sort(sorted, comparatorFor(sortColumn, sortAscending));

        for (int i = 0; i < sorted.size(); i++) {
            StudentResult s = sorted.get(i);
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setPadding(0, dp(8), 0, dp(8));
            if (i % 2 == 1) row.setBackgroundColor(stripeColor);

            String[] values = {s.admno, s.name, s.trimester, StudentResult.formatNumber(s.average), s.grade, s.remark};
            for (int col = 0; col < values.length; col++) {
                int color = textColor;
                if (col == COL_REMARK) color = s.remark.equals("PASS") ? successGreen : errorRed;
                TextView cell = gridText(values[col], color, col == COL_REMARK);
                cell.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, COLUMN_WEIGHTS[col]));
                cell.setPadding(dp(2), 0, dp(2), 0);
                row.addView(cell);
            }
            rowsContainer.addView(row);
        }
    }

    // Same font for every cell, whatever the orientation; long text wraps inside its own column
    private TextView gridText(String text, int color, boolean bold) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextColor(color);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, GRID_TEXT_SIZE_SP);
        tv.setGravity(Gravity.CENTER);
        if (bold) tv.setTypeface(Typeface.DEFAULT_BOLD);
        return tv;
    }

    // ---------------------------------------------------------------------------------------------
    // SORTING
    // ---------------------------------------------------------------------------------------------

    private static Comparator<StudentResult> comparatorFor(int column, boolean ascending) {
        Comparator<StudentResult> byAdmno = (a, b) -> compareAdmno(a.admno, b.admno);
        Comparator<StudentResult> comparator;
        switch (column) {
            case COL_NAME:    comparator = (a, b) -> a.name.compareToIgnoreCase(b.name); break;
            case COL_TRIMESTER: comparator = (a, b) -> Integer.compare(Trimester.sortKey(a.trimester), Trimester.sortKey(b.trimester)); break;
            case COL_AVERAGE: comparator = (a, b) -> Double.compare(a.average, b.average); break;
            case COL_GRADE:   comparator = (a, b) -> a.grade.compareTo(b.grade); break;
            case COL_REMARK:  comparator = (a, b) -> a.remark.compareTo(b.remark); break;
            default:          comparator = byAdmno; break;
        }
        if (!ascending) comparator = comparator.reversed();
        // Students with the same value stay in admno order (then a retaking student's trimesters in date order)
        return comparator.thenComparing(byAdmno)
                .thenComparing((a, b) -> Integer.compare(Trimester.sortKey(a.trimester), Trimester.sortKey(b.trimester)));
    }

    // Admnos compared number by number (25-0876 before 25-1002), falling back to A-Z
    private static int compareAdmno(String a, String b) {
        String digitsA = a.replaceAll("\\D", ""), digitsB = b.replaceAll("\\D", "");
        if (!digitsA.isEmpty() && !digitsB.isEmpty() && digitsA.length() == digitsB.length()) {
            int result = digitsA.compareTo(digitsB);
            if (result != 0) return result;
        }
        return a.compareToIgnoreCase(b);
    }

    // ---------------------------------------------------------------------------------------------
    // PDF DOWNLOAD (always in admno order, smallest to largest)
    // ---------------------------------------------------------------------------------------------

    private void downloadPdf() {
        List<StudentResult> byAdmno = new ArrayList<>(students);
        Collections.sort(byAdmno, comparatorFor(COL_ADMNO, true));

        String fileName = "Student_Report_"
                + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date()) + ".pdf";

        PdfDocument pdf = buildPdf(byAdmno);
        Uri savedUri = null;
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Android 10+: save to Downloads through MediaStore (no permission needed)
                ContentResolver resolver = getContentResolver();
                ContentValues values = new ContentValues();
                values.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName);
                values.put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf");
                values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
                values.put(MediaStore.MediaColumns.IS_PENDING, 1);

                savedUri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
                if (savedUri == null) throw new IOException("Could not create the file");
                try (OutputStream out = resolver.openOutputStream(savedUri)) {
                    if (out == null) throw new IOException("Could not open the file");
                    pdf.writeTo(out);
                }
                values.clear();
                values.put(MediaStore.MediaColumns.IS_PENDING, 0);
                resolver.update(savedUri, values, null, null);
            } else {
                // Android 9: save straight into the Downloads folder
                File downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                if (!downloads.exists() && !downloads.mkdirs()) throw new IOException("No Downloads folder");
                File file = new File(downloads, fileName);
                try (OutputStream out = new FileOutputStream(file)) {
                    pdf.writeTo(out);
                }
                MediaScannerConnection.scanFile(this, new String[]{file.getAbsolutePath()},
                        new String[]{"application/pdf"}, null);
            }
        } catch (IOException | RuntimeException e) {
            if (savedUri != null) getContentResolver().delete(savedUri, null, null);
            Toast.makeText(this, "Could not save the PDF: " + e.getMessage(), Toast.LENGTH_LONG).show();
            return;
        } finally {
            pdf.close();
        }

        Snackbar snackbar = Snackbar.make(findViewById(R.id.main),
                "Report saved to Downloads as " + fileName, Snackbar.LENGTH_LONG);
        if (savedUri != null) {
            final Uri uri = savedUri;
            snackbar.setAction("OPEN", v -> openPdf(uri));
        }
        snackbar.show();
    }

    private void openPdf(Uri uri) {
        Intent open = new Intent(Intent.ACTION_VIEW);
        open.setDataAndType(uri, "application/pdf");
        open.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            startActivity(open);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "No app found to open PDFs. The file is in Downloads.", Toast.LENGTH_LONG).show();
        }
    }

    // Draws the report on A4 pages, adding pages as needed (the table header repeats on each page)
    private PdfDocument buildPdf(List<StudentResult> rows) {
        final int pageWidth = 595, pageHeight = 842, margin = 40, rowHeight = 22;
        final String[] headers = {"#", "Admno", "Name", "Trimester", "Average", "Grade", "Remark"};
        final int[] widths = {25, 70, 150, 85, 65, 50, 70}; // adds up to 515 = page width - margins
        final int remarkColumn = headers.length - 1;

        Paint titlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        titlePaint.setTypeface(Typeface.DEFAULT_BOLD);
        titlePaint.setTextSize(18);
        titlePaint.setColor(Color.BLACK);

        Paint smallPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        smallPaint.setTextSize(10);
        smallPaint.setColor(Color.DKGRAY);

        TextPaint cellPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        cellPaint.setTextSize(11);

        Paint fill = new Paint();

        PdfDocument pdf = new PdfDocument();
        int pageNumber = 0, index = 0;
        String generated = new SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.US).format(new Date());

        while (index < rows.size() || pageNumber == 0) {
            pageNumber++;
            PdfDocument.Page page = pdf.startPage(
                    new PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create());
            Canvas canvas = page.getCanvas();
            int y = margin;

            if (pageNumber == 1) {
                canvas.drawText("Student Grader Report", margin, y + 14, titlePaint);
                y += 30;
                canvas.drawText("Generated " + generated + "   |   " + selectedTrimester + "   |   "
                                + rows.size() + " result(s)   |   Sorted by Admno (ascending)",
                        margin, y, smallPaint);
                y += 16;
            }

            // Table header
            fill.setColor(errorRed);
            canvas.drawRect(margin, y, pageWidth - margin, y + rowHeight, fill);
            cellPaint.setColor(Color.WHITE);
            cellPaint.setTypeface(Typeface.DEFAULT_BOLD);
            int x = margin;
            for (int c = 0; c < headers.length; c++) {
                canvas.drawText(headers[c], x + 4, y + 15, cellPaint);
                x += widths[c];
            }
            y += rowHeight;

            // Rows
            cellPaint.setTypeface(Typeface.DEFAULT);
            while (index < rows.size() && y + rowHeight <= pageHeight - margin - 20) {
                StudentResult s = rows.get(index);
                if (index % 2 == 1) {
                    fill.setColor(Color.rgb(240, 240, 240));
                    canvas.drawRect(margin, y, pageWidth - margin, y + rowHeight, fill);
                }
                String[] values = {String.valueOf(index + 1), s.admno, s.name, s.trimester,
                        StudentResult.formatNumber(s.average), s.grade, s.remark};
                x = margin;
                for (int c = 0; c < values.length; c++) {
                    cellPaint.setColor(c == remarkColumn ? (s.remark.equals("PASS") ? successGreen : errorRed) : Color.BLACK);
                    CharSequence fitted = TextUtils.ellipsize(values[c], cellPaint, widths[c] - 8, TextUtils.TruncateAt.END);
                    canvas.drawText(fitted.toString(), x + 4, y + 15, cellPaint);
                    x += widths[c];
                }
                y += rowHeight;
                index++;
            }

            canvas.drawText("Page " + pageNumber, pageWidth - margin - 40, pageHeight - margin + 10, smallPaint);
            pdf.finishPage(page);
        }
        return pdf;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
