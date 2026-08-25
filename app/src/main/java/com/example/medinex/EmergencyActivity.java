package com.example.medinex;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class EmergencyActivity extends AppCompatActivity {

    LinearLayout ambulanceBtn,
            hospitalBtn,
            doctorBtn,
            contactBtn;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_emergency);

        ambulanceBtn = findViewById(R.id.ambulanceBtn);
        hospitalBtn = findViewById(R.id.hospitalBtn);
        doctorBtn = findViewById(R.id.doctorBtn);
        contactBtn = findViewById(R.id.contactBtn);

        // Call Ambulance
        ambulanceBtn.setOnClickListener(v -> {

            Intent intent =
                    new Intent(Intent.ACTION_DIAL);

            intent.setData(Uri.parse("tel:108"));

            startActivity(intent);
        });

        // Nearby Hospital
        hospitalBtn.setOnClickListener(v -> {

            Intent intent =
                    new Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse("geo:0,0?q=hospital"));

            startActivity(intent);
        });

        // Doctor
        doctorBtn.setOnClickListener(v -> {

            Intent intent =
                    new Intent(Intent.ACTION_DIAL);

            intent.setData(
                    Uri.parse("tel:18001023999"));

            startActivity(intent);

        });

        // Emergency Contact
        contactBtn.setOnClickListener(v -> {

            Intent intent =
                    new Intent(Intent.ACTION_DIAL);

            intent.setData(
                    Uri.parse("tel:9515811687"));

            startActivity(intent);

        });
    }
}