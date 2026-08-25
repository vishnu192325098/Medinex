package com.example.medinex;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class SettingsActivity extends AppCompatActivity {

    Button logoutBtn;

    LinearLayout notificationBtn;
    LinearLayout darkModeBtn;
    LinearLayout versionBtn;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        logoutBtn = findViewById(R.id.logoutBtn);

        notificationBtn = findViewById(R.id.notificationBtn);
        darkModeBtn = findViewById(R.id.darkModeBtn);
        versionBtn = findViewById(R.id.versionBtn);

        notificationBtn.setOnClickListener(v ->
                Toast.makeText(
                        this,
                        "Medicine reminders are enabled",
                        Toast.LENGTH_SHORT
                ).show());

        darkModeBtn.setOnClickListener(v ->
                Toast.makeText(
                        this,
                        "Dark Mode is active",
                        Toast.LENGTH_SHORT
                ).show());

        versionBtn.setOnClickListener(v ->
                Toast.makeText(
                        this,
                        "Medinex Version 1.0",
                        Toast.LENGTH_SHORT
                ).show());

        logoutBtn.setOnClickListener(v -> {

            Intent intent =
                    new Intent(
                            SettingsActivity.this,
                            LoginActivity.class);

            intent.setFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK
                            | Intent.FLAG_ACTIVITY_CLEAR_TASK);

            startActivity(intent);
        });
    }
}