
package com.example.medinex;

import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.firestore.FieldValue;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public class StockPredictionActivity extends AppCompatActivity {

    private EditText medicineName, totalTablets, dailyUsage;
    private Button calculateBtn;
    private TextView resultText;
    private FirebaseDataManager dataManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_stock_prediction);

        medicineName = findViewById(R.id.medicineName);
        totalTablets = findViewById(R.id.totalTablets);
        dailyUsage = findViewById(R.id.dailyUsage);
        calculateBtn = findViewById(R.id.calculateBtn);
        resultText = findViewById(R.id.resultText);

        dataManager = new FirebaseDataManager();

        calculateBtn.setOnClickListener(v -> calculateStock());
    }

    private void calculateStock() {
        String medicine = medicineName.getText().toString().trim();
        String totalStr = totalTablets.getText().toString().trim();
        String usageStr = dailyUsage.getText().toString().trim();

        if (medicine.isEmpty() || totalStr.isEmpty() || usageStr.isEmpty()) {
            resultText.setText("Please fill all fields.");
            return;
        }

        final int total;
        final int usage;

        try {
            total = Integer.parseInt(totalStr);
            usage = Integer.parseInt(usageStr);
        } catch (NumberFormatException e) {
            resultText.setText("Enter valid whole numbers.");
            return;
        }

        if (total <= 0 || usage <= 0) {
            resultText.setText(
                    "Stock and daily usage must be greater than zero."
            );
            return;
        }

        int daysRemaining = total / usage;

        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.DAY_OF_YEAR, daysRemaining);

        String refillDate = new SimpleDateFormat(
                "dd MMM yyyy",
                Locale.getDefault()
        ).format(calendar.getTime());

        String status;
        String urgency;
        String recommendation;

        if (daysRemaining <= 3) {
            status = "CRITICAL";
            urgency = "HIGH";
            recommendation = "Plan a refill promptly.";
        } else if (daysRemaining <= 7) {
            status = "WARNING";
            urgency = "MEDIUM";
            recommendation = "Plan a refill soon.";
        } else {
            status = "SAFE";
            urgency = "LOW";
            recommendation = "Current stock is estimated to last longer.";
        }

        String result =
                "📦 MEDINEX STOCK PREDICTION REPORT\n\n" +
                        "💊 Medicine Name:\n" + medicine + "\n\n" +
                        "📦 Current Stock:\n" + total + " Tablets\n\n" +
                        "💊 Daily Consumption:\n" + usage + " Tablets/Day\n\n" +
                        "📅 Predicted Remaining:\n" + daysRemaining + " Days\n\n" +
                        "🗓 Expected Exhaustion Date:\n" + refillDate + "\n\n" +
                        "⚠ STOCK STATUS: " + status + "\n\n" +
                        "AI Recommendation:\n" + recommendation + "\n\n" +
                        "Refill Urgency: " + urgency + "\n\n" +
                        "📊 STOCK ANALYSIS COMPLETE";

        resultText.setText(result);

        savePrediction(
                medicine,
                total,
                usage,
                daysRemaining,
                refillDate,
                status,
                urgency,
                recommendation
        );
    }

    private void savePrediction(
            String medicine,
            int total,
            int usage,
            int daysRemaining,
            String refillDate,
            String status,
            String urgency,
            String recommendation
    ) {
        Map<String, Object> record = new HashMap<>();
        record.put("medicineName", medicine);
        record.put("totalTablets", total);
        record.put("dailyUsage", usage);
        record.put("daysRemaining", daysRemaining);
        record.put("predictedExhaustionDate", refillDate);
        record.put("stockStatus", status);
        record.put("refillUrgency", urgency);
        record.put("recommendation", recommendation);
        record.put("createdAt", FieldValue.serverTimestamp());

        calculateBtn.setEnabled(false);

        dataManager.saveRecord(
                "stockPredictions",
                UUID.randomUUID().toString(),
                record,
                (success, message) -> {
                    calculateBtn.setEnabled(true);

                    Toast.makeText(
                            this,
                            success
                                    ? "Prediction saved to Firebase"
                                    : "Saving failed: " + message,
                            success ? Toast.LENGTH_SHORT : Toast.LENGTH_LONG
                    ).show();
                }
        );
    }
}
