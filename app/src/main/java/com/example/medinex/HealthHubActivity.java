package com.example.medinex;

import android.content.Intent;
import android.os.Bundle;
import android.widget.LinearLayout;

import androidx.appcompat.app.AppCompatActivity;

public class HealthHubActivity extends AppCompatActivity {

    LinearLayout aboutBtn, bmiBtn, tipsBtn, waterBtn, appleBtn;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_health_hub);

        aboutBtn = findViewById(R.id.aboutBtn);
        bmiBtn = findViewById(R.id.bmiBtn);
        tipsBtn = findViewById(R.id.tipsBtn);
        waterBtn = findViewById(R.id.waterBtn);
        appleBtn = findViewById(R.id.appleBtn);

        aboutBtn.setOnClickListener(v ->
                startActivity(new Intent(this, AboutActivity.class)));

        bmiBtn.setOnClickListener(v ->
                startActivity(new Intent(this, BMIActivity.class)));

        tipsBtn.setOnClickListener(v ->
                startActivity(new Intent(this, HealthTipsActivity.class)));

        waterBtn.setOnClickListener(v ->
                startActivity(new Intent(this, WaterTrackerActivity.class)));

        appleBtn.setOnClickListener(v ->
                startActivity(new Intent(this, AppleActivity.class)));
    }
}