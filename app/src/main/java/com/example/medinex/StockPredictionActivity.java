package com.example.medinex;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

public class StockPredictionActivity extends AppCompatActivity {

    EditText medicineName, totalTablets, dailyUsage;
    Button calculateBtn;
    TextView resultText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_stock_prediction);

        medicineName = findViewById(R.id.medicineName);
        totalTablets = findViewById(R.id.totalTablets);
        dailyUsage = findViewById(R.id.dailyUsage);
        calculateBtn = findViewById(R.id.calculateBtn);
        resultText = findViewById(R.id.resultText);

        calculateBtn.setOnClickListener(v -> {

            String medicine =
                    medicineName.getText().toString();

            String totalStr =
                    totalTablets.getText().toString();

            String usageStr =
                    dailyUsage.getText().toString();

            if (medicine.isEmpty()
                    || totalStr.isEmpty()
                    || usageStr.isEmpty()) {

                resultText.setText(
                        "Please fill all fields");
                return;
            }

            int total =
                    Integer.parseInt(totalStr);

            int usage =
                    Integer.parseInt(usageStr);

            int daysRemaining =
                    total / usage;
            Calendar calendar = Calendar.getInstance();
            calendar.add(Calendar.DAY_OF_YEAR, daysRemaining);

            String refillDate =
                    new SimpleDateFormat(
                            "dd MMM yyyy",
                            Locale.getDefault())
                            .format(calendar.getTime());

            String result =
                    "📦 MEDINEX STOCK PREDICTION REPORT\n\n" +

                            "💊 Medicine Name:\n" +
                            medicine + "\n\n" +

                            "📦 Current Stock:\n" +
                            total + " Tablets\n\n" +

                            "💊 Daily Consumption:\n" +
                            usage + " Tablets/Day\n\n" +

                            "📅 Predicted Remaining:\n" +
                            daysRemaining + " Days\n\n" +

                            "🗓 Expected Exhaustion Date:\n" +
                            refillDate + "\n\n";

            if (daysRemaining <= 3) {

                result +=
                        "🚨 STOCK STATUS: CRITICAL\n\n" +

                                "Risk:\n" +
                                "Medicine stock will finish very soon.\n\n" +

                                "AI Recommendation:\n" +
                                "Purchase medicine immediately.\n\n" +

                                "Refill Urgency: HIGH\n";

            }
            else if (daysRemaining <= 7) {

                result +=
                        "⚠ STOCK STATUS: WARNING\n\n" +

                                "Risk:\n" +
                                "Stock running low.\n\n" +

                                "AI Recommendation:\n" +
                                "Refill within the next few days.\n\n" +

                                "Refill Urgency: MEDIUM\n";

            }
            else {

                result +=
                        "✅ STOCK STATUS: SAFE\n\n" +

                                "Risk:\n" +
                                "No immediate shortage expected.\n\n" +

                                "AI Recommendation:\n" +
                                "Current stock is sufficient.\n\n" +

                                "Refill Urgency: LOW\n";
            }

            result +=
                    "\n📊 STOCK ANALYSIS COMPLETE";

            resultText.setText(result);
        });
    }
}