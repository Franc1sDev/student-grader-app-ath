package com.example.myfirstappath;

import android.content.Intent;
import android.os.Bundle;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.card.MaterialCardView;

public class dashboard extends AppCompatActivity {

    MaterialCardView addMarks, viewReport;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.dashboard);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            // systemBars + ime: when the keypad opens, the page shrinks above it and becomes scrollable,
            // so the keypad never covers a field or button
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.ime());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        addMarks = findViewById(R.id.btnCard_addMarks);
        viewReport = findViewById(R.id.btnCard_viewReport);

        // "Add Student Marks" card opens the marks page
        addMarks.setOnClickListener(view -> startActivity(new Intent(dashboard.this, marksPage.class)));

        // "View Report" card opens the report page
        viewReport.setOnClickListener(view -> startActivity(new Intent(dashboard.this, reportsPage.class)));
    }
}
