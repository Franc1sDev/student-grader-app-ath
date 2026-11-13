package com.franc1sdev.tally;//project identity

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class splashScreen extends AppCompatActivity {//container/page

    private static final int DELAY_MS = 2500;//variable declared

    @Override//polymorphism
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);//ensures that the activity's screen runs corner to corner on the device
        setContentView(R.layout.splash_screen);
        //a view is a UI component - button, textview, image, etc.
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {//start of insets
                    Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                    v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
                    return insets;
                });//end of insets

                //insets ensures that as edgetoEdge fills the activity on the entire screen, it does not block the Android screen's
                //notification bar, home button, preview button and back button.

            //code for splash screen:
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                Intent moveToLoginPage = new Intent(splashScreen.this, loginPage.class);
                startActivity(moveToLoginPage);
                finish();//to ensure that the user cannot go back to the splash screen
            }, DELAY_MS);
            }
        }