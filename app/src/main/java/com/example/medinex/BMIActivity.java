package com.example.medinex;

import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

public class BMIActivity extends AppCompatActivity {

    EditText weightInput, heightInput;
    Button calculateBtn;
    TextView resultText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_bmi);
        weightInput = findViewById(R.id.weightInput);
        heightInput = findViewById(R.id.heightInput);
        calculateBtn = findViewById(R.id.calculateBtn);
        resultText = findViewById(R.id.resultText);

        calculateBtn.setOnClickListener(v -> {

            String weightStr =
                    weightInput.getText().toString();

            String heightStr =
                    heightInput.getText().toString();

            if(weightStr.isEmpty() || heightStr.isEmpty()) {

                resultText.setText(
                        "Please enter weight and height");
                return;
            }

            double weight =
                    Double.parseDouble(weightStr);

            double height =
                    Double.parseDouble(heightStr);

            double bmi =
                    weight / (height * height);

            String status;

            if(bmi < 18.5) {

                status = "Underweight";

            } else if(bmi < 25) {

                status = "Normal";

            } else if(bmi < 30) {

                status = "Overweight";

            } else {

                status = "Obese";
            }

            resultText.setText(
                    "⚖ BMI REPORT\n\n" +
                            "Weight: " + weight + " kg\n" +
                            "Height: " + height + " m\n\n" +
                            "BMI: " + String.format("%.2f", bmi) + "\n\n" +
                            "Status: " + status
            );
        });
    }
}