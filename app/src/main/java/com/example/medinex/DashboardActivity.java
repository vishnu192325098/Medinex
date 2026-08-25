package com.example.medinex;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

public class DashboardActivity extends AppCompatActivity {

    LinearLayout uploadBtn,
            emergencyBtn,
            reminderBtn,
            stockBtn,
            duplicateBtn,
            interactionBtn,
            aiBtn,
            dietBtn,
            healthHubBtn;

    TextView welcomeText;
    TextView profileCircle;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dashboard);

        welcomeText = findViewById(R.id.welcomeText);
        profileCircle = findViewById(R.id.profileCircle);

        SharedPreferences prefs =
                getSharedPreferences(
                        "MedinexUser",
                        MODE_PRIVATE);

        String userName =
                prefs.getString(
                        "userName",
                        "User");

        welcomeText.setText(
                "Welcome " + userName + " 👋");

        if (!userName.isEmpty()) {

            String firstLetter =
                    userName.substring(0, 1)
                            .toUpperCase();

            profileCircle.setText(firstLetter);
        }

        uploadBtn = findViewById(R.id.uploadBtn);
        emergencyBtn = findViewById(R.id.emergencyBtn);
        reminderBtn = findViewById(R.id.reminderBtn);
        stockBtn = findViewById(R.id.stockBtn);
        duplicateBtn = findViewById(R.id.duplicateBtn);
        interactionBtn = findViewById(R.id.interactionBtn);
        aiBtn = findViewById(R.id.aiBtn);
        dietBtn = findViewById(R.id.dietBtn);

        // Health Hub Button
        healthHubBtn = findViewById(R.id.healthHubBtn);

        uploadBtn.setOnClickListener(v ->
                startActivity(new Intent(this, UploadActivity.class)));

        emergencyBtn.setOnClickListener(v ->
                startActivity(new Intent(this, EmergencyActivity.class)));

        reminderBtn.setOnClickListener(v ->
                startActivity(new Intent(this, ReminderSetupActivity.class)));

        stockBtn.setOnClickListener(v ->
                startActivity(new Intent(this, StockPredictionActivity.class)));

        duplicateBtn.setOnClickListener(v ->
                startActivity(new Intent(this, DuplicateMedicineActivity.class)));

        interactionBtn.setOnClickListener(v ->
                startActivity(new Intent(this, MedicineInteractionActivity.class)));

        aiBtn.setOnClickListener(v ->
                startActivity(new Intent(this, AIHealthAssistantActivity.class)));

        dietBtn.setOnClickListener(v ->
                startActivity(new Intent(this, DietPlannerActivity.class)));

        healthHubBtn.setOnClickListener(v ->
                startActivity(new Intent(this, HealthHubActivity.class)));

        profileCircle.setOnClickListener(v -> {

            Intent intent =
                    new Intent(
                            DashboardActivity.this,
                            ProfileActivity.class);

            startActivity(intent);

        });
    }
}