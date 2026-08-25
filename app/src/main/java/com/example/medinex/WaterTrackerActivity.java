package com.example.medinex;

import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

public class WaterTrackerActivity extends AppCompatActivity {

    TextView waterCountText, statusText;
    Button addWaterBtn;

    int glasses = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_water_tracker);

        waterCountText = findViewById(R.id.waterCountText);
        statusText = findViewById(R.id.statusText);
        addWaterBtn = findViewById(R.id.addWaterBtn);

        addWaterBtn.setOnClickListener(v -> {

            glasses++;

            waterCountText.setText(
                    "Glasses Drunk: " + glasses);

            if(glasses >= 8)
            {
                statusText.setText(
                        "✅ Daily Water Goal Achieved!");
            }
            else
            {
                statusText.setText(
                        "💧 Remaining: " +
                                (8 - glasses) +
                                " Glasses");
            }
        });
    }
}