package com.example.myfirstappath;

import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.button.MaterialButton;

public class summaryPage extends AppCompatActivity {

    // Key used by the marks page to tell this page which student to show
    public static final String EXTRA_ADMNO = "admno";
    public static final String EXTRA_TRIMESTER = "trimester";

    TextView summary;
    MaterialButton viewFullReport;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.summary_page);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.ime());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });

        summary = findViewById(R.id.txtvw_summary);
        viewFullReport = findViewById(R.id.btn_viewFullReport);

        // Read the student from the database and show his summary
        String admno = getIntent().getStringExtra(EXTRA_ADMNO);
        String trimester = getIntent().getStringExtra(EXTRA_TRIMESTER);
        StudentResult student = null;
        if (admno != null && trimester != null) {
            DatabaseHelper db = new DatabaseHelper(this);
            student = db.getStudentResult(admno, trimester);
            db.close();
        }

        if (student == null) {
            summary.setText("No student summary to show.");
        } else {
            summary.setText(student.name
                    + "\n\n" + student.admno
                    + "\n\n" + student.trimester
                    + "\n\nAVERAGE: " + StudentResult.formatNumber(student.average)
                    + "\n\nGRADE: " + student.grade
                    + "\n\nREMARK: " + student.remark);
        }

        // VIEW FULL REPORT opens the report page
        viewFullReport.setOnClickListener(view -> {
            Intent moveToReportsPage = new Intent(summaryPage.this, reportsPage.class);
            startActivity(moveToReportsPage);
        });
    }
}
