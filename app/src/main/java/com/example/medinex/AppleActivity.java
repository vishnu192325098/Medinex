package com.example.medinex;

import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

public class AppleActivity extends AppCompatActivity {

    EditText conditionInput;
    Button generateBtn;
    TextView resultText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_apple);

        conditionInput = findViewById(R.id.conditionInput);
        generateBtn = findViewById(R.id.generateBtn);
        resultText = findViewById(R.id.resultText);

        generateBtn.setOnClickListener(v -> {

            String condition =
                    conditionInput.getText()
                            .toString()
                            .trim()
                            .toLowerCase();

            if (condition.contains("fever")) {

                resultText.setText(
                        "🍎 Recommended Fruits\n\n" +
                                "• Apple\n" +
                                "• Orange\n" +
                                "• Kiwi\n\n" +

                                "🩹 First Aid\n\n" +
                                "• Drink plenty of water\n" +
                                "• Take proper rest\n" +
                                "• Monitor temperature"
                );

            } else if (condition.contains("cold")) {

                resultText.setText(
                        "🍎 Recommended Fruits\n\n" +
                                "• Orange\n" +
                                "• Lemon\n" +
                                "• Kiwi\n\n" +

                                "🩹 First Aid\n\n" +
                                "• Drink warm water\n" +
                                "• Steam inhalation\n" +
                                "• Get enough rest"
                );

            } else if (condition.contains("dengue")) {

                resultText.setText(
                        "🍎 Recommended Fruits\n\n" +
                                "• Papaya\n" +
                                "• Apple\n" +
                                "• Orange\n\n" +

                                "🩹 First Aid\n\n" +
                                "• Stay hydrated\n" +
                                "• Drink ORS\n" +
                                "• Consult a doctor"
                );

            } else {

                resultText.setText(
                        "No advice available for this condition.\n\n" +
                                "Try: Fever, Cold, or Dengue."
                );
            }
        });
    }
}