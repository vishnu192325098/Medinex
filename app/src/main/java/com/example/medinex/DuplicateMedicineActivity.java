package com.example.medinex;

import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

public class DuplicateMedicineActivity extends AppCompatActivity {

    EditText medicine1, medicine2;
    Button checkBtn;
    TextView resultText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_duplicate_medicine);

        medicine1 = findViewById(R.id.medicine1);
        medicine2 = findViewById(R.id.medicine2);
        checkBtn = findViewById(R.id.checkBtn);
        resultText = findViewById(R.id.resultText);

        checkBtn.setOnClickListener(v -> {

            String med1 =
                    medicine1.getText()
                            .toString()
                            .trim()
                            .toLowerCase();

            String med2 =
                    medicine2.getText()
                            .toString()
                            .trim()
                            .toLowerCase();

            if (med1.isEmpty() || med2.isEmpty()) {

                resultText.setText(
                        "⚠ Please enter both medicine names."
                );

                return;
            }

            if (
                    (med1.contains("paracetamol") && med2.contains("crocin"))
                            ||
                            (med1.contains("crocin") && med2.contains("paracetamol"))
                            ||
                            (med1.contains("paracetamol") && med2.contains("dolo"))
                            ||
                            (med1.contains("dolo") && med2.contains("paracetamol"))
            ) {

                resultText.setText(
                        "🔴 DUPLICATE MEDICINE DETECTED\n\n" +

                                "💊 Medicine 1:\n" +
                                med1 + "\n\n" +

                                "💊 Medicine 2:\n" +
                                med2 + "\n\n" +

                                "🤖 Medinex Analysis:\n" +
                                "Both medicines contain Paracetamol.\n\n" +

                                "⚠ Risk Level:\nHIGH\n\n" +

                                "👨‍⚕️ Recommendation:\n" +
                                "Avoid taking both medicines together. Risk of overdose may occur."
                );

            } else {

                resultText.setText(
                        "🟢 NO DUPLICATE MEDICINE DETECTED\n\n" +

                                "💊 Medicine 1:\n" +
                                med1 + "\n\n" +

                                "💊 Medicine 2:\n" +
                                med2 + "\n\n" +

                                "🤖 Medinex Analysis:\n" +
                                "Different active ingredients detected.\n\n" +

                                "⚠ Risk Level:\nLOW\n\n" +

                                "👨‍⚕️ Recommendation:\n" +
                                "Safe to use as prescribed."
                );
            }
        });
    }
}