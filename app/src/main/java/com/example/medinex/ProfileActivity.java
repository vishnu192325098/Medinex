package com.example.medinex;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

public class ProfileActivity extends AppCompatActivity {

    TextView profileCircle;
    TextView nameText;
    TextView emailText;
    TextView phoneText;

    Button logoutBtn;
    Button settingsBtn;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);

        profileCircle = findViewById(R.id.profileCircle);
        nameText = findViewById(R.id.nameText);
        emailText = findViewById(R.id.emailText);
        phoneText = findViewById(R.id.phoneText);

        logoutBtn = findViewById(R.id.logoutBtn);
        settingsBtn = findViewById(R.id.settingsBtn);

        SharedPreferences prefs =
                getSharedPreferences(
                        "MedinexUser",
                        MODE_PRIVATE);

        String name =
                prefs.getString(
                        "userName",
                        "User");

        String email =
                prefs.getString(
                        "userEmail",
                        "Not Available");

        String phone =
                prefs.getString(
                        "userPhone",
                        "Not Available");

        nameText.setText("Name: " + name);
        emailText.setText("Email: " + email);
        phoneText.setText("Phone: " + phone);

        if (!name.isEmpty()) {

            String firstLetter =
                    name.substring(0, 1).toUpperCase();

            profileCircle.setText(firstLetter);
        }

        settingsBtn.setOnClickListener(v -> {

            Intent intent =
                    new Intent(
                            ProfileActivity.this,
                            SettingsActivity.class);

            startActivity(intent);
        });

        logoutBtn.setOnClickListener(v -> {

            prefs.edit().clear().apply();

            Intent intent =
                    new Intent(
                            ProfileActivity.this,
                            LoginActivity.class);

            startActivity(intent);

            finishAffinity();
        });
    }
}