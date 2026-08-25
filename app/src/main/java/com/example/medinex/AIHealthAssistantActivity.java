package com.example.medinex;

import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

public class AIHealthAssistantActivity extends AppCompatActivity {

    EditText symptomInput;
    Button analyzeBtn;
    TextView resultText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_aihealth_assistant);

        symptomInput = findViewById(R.id.symptomInput);
        analyzeBtn = findViewById(R.id.analyzeBtn);
        resultText = findViewById(R.id.resultText);

        analyzeBtn.setOnClickListener(v -> {

            String symptom = symptomInput.getText()
                    .toString()
                    .toLowerCase()
                    .trim();

            if (symptom.isEmpty()) {
                resultText.setText("⚠ Please enter symptoms.");
                return;
            }


            if (symptom.contains("emergency")
                    || symptom.contains("unconscious")
                    || symptom.contains("accident")) {

                resultText.setText(
                        "🚨 EMERGENCY DETECTED\n\n" +
                                "🏥 Please contact emergency services immediately.\n\n" +
                                "📞 Use the Emergency Support module in Medinex."
                );

                return;
            }

// Fever Analysis
            if (symptom.contains("fever")
                    || symptom.contains("headache")
                    || symptom.contains("body pain")) {


            }

            // Fever Analysis
            if (symptom.contains("fever")
                    || symptom.contains("headache")
                    || symptom.contains("body pain")) {

                resultText.setText(
                        "🤖 MEDINEX AI REPORT\n\n" +

                                "🩺 Symptoms Detected:\n" +
                                "• Fever\n" +
                                "• Headache / Body Pain\n\n" +

                                "🔍 Possible Conditions:\n" +
                                "• Viral Fever\n" +
                                "• Common Flu\n" +
                                "• Mild Infection\n\n" +

                                "🟢 Severity Level:\n" +
                                "LOW RISK\n\n" +

                                "💡 Recommendations:\n" +
                                "• Drink plenty of fluids\n" +
                                "• Take adequate rest\n" +
                                "• Monitor body temperature\n" +
                                "• Eat nutritious food\n\n" +
                                "💊 Commonly Used Medicines:\n" +
                                "• Paracetamol 500mg\n\n" +

                                "⏰ General Timing:\n" +
                                "• Usually every 6-8 hours if advised\n" +
                                "• Follow package instructions\n\n" +

                                "⚠ Disclaimer:\n" +
                                "Consult a doctor if symptoms persist for more than 3 days."
                );

            }

            // Cough Analysis
            else if (symptom.contains("cough")
                    || symptom.contains("cold")
                    || symptom.contains("sore throat")) {

                resultText.setText(
                        "🤖 MEDINEX AI REPORT\n\n" +

                                "🩺 Symptoms Detected:\n" +
                                "• Cough\n" +
                                "• Cold / Sore Throat\n\n" +

                                "🔍 Possible Conditions:\n" +
                                "• Common Cold\n" +
                                "• Throat Infection\n" +
                                "• Seasonal Allergy\n\n" +

                                "🟡 Severity Level:\n" +
                                "MEDIUM RISK\n\n" +

                                "💡 Recommendations:\n" +
                                "• Drink warm water\n" +
                                "• Avoid cold beverages\n" +
                                "• Take sufficient rest\n" +
                                "• Gargle with salt water\n\n" +
                                "💊 Commonly Used Medicines:\n" +
                                "• Cough Syrup\n" +
                                "• Levocetirizine\n\n" +

                                "⏰ General Timing:\n" +
                                "• As directed by a healthcare professional\n\n" +

                                "⚠ Disclaimer:\n" +
                                "Visit a doctor if breathing difficulty occurs."
                );

            }

            // Stomach Analysis
            else if (symptom.contains("stomach")
                    || symptom.contains("vomit")
                    || symptom.contains("diarrhea")) {

                resultText.setText(
                        "🤖 MEDINEX AI REPORT\n\n" +

                                "🩺 Symptoms Detected:\n" +
                                "• Stomach Discomfort\n" +
                                "• Digestive Issues\n\n" +

                                "🔍 Possible Conditions:\n" +
                                "• Indigestion\n" +
                                "• Food Poisoning\n" +
                                "• Gastric Irritation\n\n" +

                                "🟡 Severity Level:\n" +
                                "MEDIUM RISK\n\n" +

                                "💡 Recommendations:\n" +
                                "• Drink plenty of water\n" +
                                "• Eat light food\n" +
                                "• Avoid oily food\n" +
                                "• Take proper rest\n\n" +
                                "💊 Commonly Used Medicines:\n" +
                                "• Antacid Tablets\n" +
                                "• Oral Rehydration Solution (ORS)\n\n" +

                                "⏰ General Timing:\n" +
                                "• Generally after medical advice\n\n" +

                                "⚠ Disclaimer:\n" +
                                "Seek medical advice if symptoms worsen."
                );

            }

            // Chest Pain Analysis
            else if ((symptom.contains("chest")
                    && symptom.contains("breath"))
                    || (symptom.contains("heart")
                    && symptom.contains("pain"))) {

                resultText.setText(
                        "🚨 MEDINEX EMERGENCY ALERT\n\n" +

                                "🔴 CRITICAL RISK DETECTED\n\n" +

                                "🩺 Symptoms Detected:\n" +
                                "• Chest Pain\n" +
                                "• Breathing Difficulty\n\n" +

                                "⚠ Possible Conditions:\n" +
                                "• Heart Attack\n" +
                                "• Cardiac Emergency\n" +
                                "• Serious Respiratory Condition\n\n" +

                                "🏥 Immediate Action Required:\n" +
                                "• Contact Emergency Services\n" +
                                "• Inform Family Members\n" +
                                "• Do Not Ignore Symptoms\n" +
                                "• Seek Immediate Medical Care\n\n" +

                                "🚨 EMERGENCY LEVEL: CRITICAL"
                );

            }

            else if (symptom.contains("chest")
                    || symptom.contains("heart")
                    || symptom.contains("breath")) {

                resultText.setText(
                        "🤖 MEDINEX AI REPORT\n\n" +

                                "🩺 Symptoms Detected:\n" +
                                "• Chest Discomfort\n" +
                                "• Breathing Issues\n\n" +

                                "🔍 Possible Conditions:\n" +
                                "• Respiratory Problem\n" +
                                "• Cardiac Condition\n\n" +

                                "🔴 Severity Level:\n" +
                                "HIGH RISK\n\n" +

                                "💡 Recommendations:\n" +
                                "• Seek immediate medical attention\n" +
                                "• Contact emergency services\n" +
                                "• Do not ignore symptoms\n\n" +

                                "🚨 Emergency Warning:\n" +
                                "Immediate medical evaluation recommended."
                );

            }
            else if (symptom.contains("diabetes")
                    || symptom.contains("sugar")) {

                resultText.setText(
                        "🤖 MEDINEX AI REPORT\n\n" +

                                "🩺 Symptoms Detected:\n" +
                                "• Diabetes Related Concern\n\n" +

                                "🔍 Possible Conditions:\n" +
                                "• High Blood Sugar\n" +
                                "• Type 2 Diabetes\n\n" +

                                "🟡 Severity Level:\n" +
                                "MEDIUM RISK\n\n" +

                                "💡 Recommendations:\n" +
                                "• Monitor blood sugar regularly\n" +
                                "• Avoid sugary foods\n" +
                                "• Stay physically active\n\n" +
                                "💊 Commonly Used Medicines:\n" +
                                "• Metformin (commonly prescribed)\n\n" +

                                "⏰ General Timing:\n" +
                                "• Only as prescribed by a doctor\n\n" +

                                "⚠ Doctor Consultation Recommended."
                );
            }
            else if (symptom.contains("bp")
                    || symptom.contains("blood pressure")
                    || symptom.contains("hypertension")) {

                resultText.setText(
                        "🤖 MEDINEX AI REPORT\n\n" +

                                "🩺 Symptoms Detected:\n" +
                                "• Blood Pressure Concern\n\n" +

                                "🔍 Possible Conditions:\n" +
                                "• Hypertension\n" +
                                "• Elevated Blood Pressure\n\n" +

                                "🟡 Severity Level:\n" +
                                "MEDIUM RISK\n\n" +

                                "💡 Recommendations:\n" +
                                "• Reduce salt intake\n" +
                                "• Exercise regularly\n" +
                                "• Monitor BP frequently\n\n" +
                                "💊 Commonly Used Medicines:\n" +
                                "• Common BP medicines may be prescribed by doctors\n\n" +

                                "⏰ General Timing:\n" +
                                "• Follow doctor's instructions exactly\n\n" +

                                "⚠ Seek medical advice if BP remains high."
                );
            }
            else if (symptom.contains("dengue")) {

                resultText.setText(
                        "🤖 MEDINEX AI REPORT\n\n" +

                                "🩺 Symptoms Detected:\n" +
                                "• Dengue Related Symptoms\n\n" +

                                "🔍 Possible Conditions:\n" +
                                "• Dengue Fever\n\n" +

                                "🔴 Severity Level:\n" +
                                "HIGH RISK\n\n" +

                                "💡 Recommendations:\n" +
                                "• Drink plenty of fluids\n" +
                                "• Monitor platelet count\n" +
                                "• Take complete rest\n\n" +

                                "🚨 Immediate medical supervision advised."
                );
            }
            else if (symptom.contains("typhoid")) {

                resultText.setText(
                        "🤖 MEDINEX AI REPORT\n\n" +

                                "🩺 Symptoms Detected:\n" +
                                "• Typhoid Related Symptoms\n\n" +

                                "🔍 Possible Conditions:\n" +
                                "• Typhoid Fever\n\n" +

                                "🟠 Severity Level:\n" +
                                "HIGH RISK\n\n" +

                                "💡 Recommendations:\n" +
                                "• Eat soft foods\n" +
                                "• Stay hydrated\n" +
                                "• Complete prescribed treatment\n\n" +

                                "⚠ Doctor supervision required."
                );
            }
            else if (symptom.contains("migraine")) {

                resultText.setText(
                        "🤖 MEDINEX AI REPORT\n\n" +

                                "🩺 Symptoms Detected:\n" +
                                "• Migraine Symptoms\n\n" +

                                "🔍 Possible Conditions:\n" +
                                "• Migraine Headache\n\n" +

                                "🟡 Severity Level:\n" +
                                "MEDIUM RISK\n\n" +

                                "💡 Recommendations:\n" +
                                "• Rest in a dark room\n" +
                                "• Stay hydrated\n" +
                                "• Reduce screen exposure\n\n" +

                                "⚠ Consult a doctor if frequent."
                );
            }
            else if (symptom.contains("asthma")) {

                resultText.setText(
                        "🤖 MEDINEX AI REPORT\n\n" +

                                "🩺 Symptoms Detected:\n" +
                                "• Asthma Related Symptoms\n\n" +

                                "🔍 Possible Conditions:\n" +
                                "• Asthma Attack\n" +
                                "• Airway Inflammation\n\n" +

                                "🔴 Severity Level:\n" +
                                "HIGH RISK\n\n" +

                                "💡 Recommendations:\n" +
                                "• Use prescribed inhaler\n" +
                                "• Avoid dust and smoke\n" +
                                "• Seek medical care if breathing worsens\n\n" +

                                "⚠ Doctor consultation recommended."
                );
            }
            else if (symptom.contains("covid")
                    || symptom.contains("corona")) {

                resultText.setText(
                        "🤖 MEDINEX AI REPORT\n\n" +

                                "🩺 Symptoms Detected:\n" +
                                "• COVID Related Symptoms\n\n" +

                                "🔍 Possible Conditions:\n" +
                                "• COVID-19 Infection\n\n" +

                                "🟠 Severity Level:\n" +
                                "MEDIUM TO HIGH RISK\n\n" +

                                "💡 Recommendations:\n" +
                                "• Isolate if necessary\n" +
                                "• Drink plenty of fluids\n" +
                                "• Monitor oxygen levels\n" +
                                "• Consult healthcare professionals\n\n" +

                                "⚠ Seek medical attention if breathing difficulty occurs."
                );
            }


            else {

                resultText.setText(
                        "🤖 MEDINEX AI REPORT\n\n" +

                                "No matching condition found.\n\n" +

                                "💡 Recommendation:\n" +
                                "Please provide more detailed symptoms.\n\n" +

                                "⚠ This AI assistant is for educational purposes only."
                );
            }
        });
    }
}