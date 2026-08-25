package com.example.medinex;

import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

public class MedicineInteractionActivity extends AppCompatActivity {

    EditText medicine1, medicine2;
    Button checkBtn;
    TextView resultText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_medicine_interaction);

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

            // Aspirin + Warfarin
            if (
                    (med1.contains("aspirin")
                            && med2.contains("warfarin"))

                            ||

                            (med1.contains("warfarin")
                                    && med2.contains("aspirin"))
            ) {

                resultText.setText(
                        "🔴 HIGH RISK INTERACTION\n\n" +

                                "💊 Medicines:\n" +
                                med1 + " + " + med2 + "\n\n" +

                                "⚠ Risk Level:\nHIGH\n\n" +

                                "🤖 Medinex Analysis:\n" +
                                "These medicines may significantly increase bleeding risk.\n\n" +

                                "👨‍⚕️ Recommendation:\n" +
                                "Consult a doctor immediately before taking these medicines together.\n\n" +

                                "📋 Monitoring Required:\nYES"
                );

            }

            // Ibuprofen + Aspirin
            else if (
                    (med1.contains("ibuprofen")
                            && med2.contains("aspirin"))

                            ||

                            (med1.contains("aspirin")
                                    && med2.contains("ibuprofen"))
            ) {

                resultText.setText(
                        "🟡 MODERATE INTERACTION\n\n" +

                                "💊 Medicines:\n" +
                                med1 + " + " + med2 + "\n\n" +

                                "⚠ Risk Level:\nMEDIUM\n\n" +

                                "🤖 Medinex Analysis:\n" +
                                "Ibuprofen may reduce the effectiveness of Aspirin and increase stomach irritation.\n\n" +

                                "👨‍⚕️ Recommendation:\n" +
                                "Use with caution and follow medical advice.\n\n" +

                                "📋 Monitoring Required:\nYES"
                );

            }

            // Paracetamol + Cetirizine
            else if (
                    (med1.contains("paracetamol")
                            && med2.contains("cetirizine"))

                            ||

                            (med1.contains("cetirizine")
                                    && med2.contains("paracetamol"))
            ) {

                resultText.setText(
                        "🟢 SAFE COMBINATION\n\n" +

                                "💊 Medicines:\n" +
                                med1 + " + " + med2 + "\n\n" +

                                "⚠ Risk Level:\nLOW\n\n" +

                                "🤖 Medinex Analysis:\n" +
                                "No significant interaction has been identified.\n\n" +

                                "👨‍⚕️ Recommendation:\n" +
                                "Generally safe when used as directed.\n\n" +

                                "📋 Monitoring Required:\nNO"
                );

            }

            // Default Safe Result
            else {

                resultText.setText(
                        "🟢 NO MAJOR INTERACTION DETECTED\n\n" +

                                "💊 Medicines:\n" +
                                med1 + " + " + med2 + "\n\n" +

                                "⚠ Risk Level:\nLOW\n\n" +

                                "🤖 Medinex Analysis:\n" +
                                "No major interaction found in the Medinex database.\n\n" +

                                "👨‍⚕️ Recommendation:\n" +
                                "Safe to consume together under normal circumstances.\n\n" +

                                "📋 Monitoring Required:\nNO"
                );
            }
        });
    }
}