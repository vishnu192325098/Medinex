package com.example.medinex;

import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

public class DietPlannerActivity extends AppCompatActivity {

    EditText conditionInput;
    Button generateBtn;
    TextView resultText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_diet_planner);

        conditionInput = findViewById(R.id.conditionInput);
        generateBtn = findViewById(R.id.generateBtn);
        resultText = findViewById(R.id.resultText);

        generateBtn.setOnClickListener(v -> {

            String condition =
                    conditionInput.getText()
                            .toString()
                            .toLowerCase()
                            .trim();

            if (condition.contains("fever")) {

                resultText.setText(
                        "🥗 FEVER DIET PLAN\n\n" +

                                "Morning:\n" +
                                "• Warm Water\n" +
                                "• Idli\n" +
                                "• Banana\n\n" +

                                "Lunch:\n" +
                                "• Rice\n" +
                                "• Dal\n" +
                                "• Boiled Vegetables\n\n" +

                                "Evening:\n" +
                                "• Fruit Juice\n" +
                                "• Apple\n\n" +

                                "Dinner:\n" +
                                "• Khichdi\n" +
                                "• Soup\n\n" +

                                "Avoid:\n" +
                                "• Ice Cream\n" +
                                "• Cold Drinks\n" +
                                "• Fried Foods\n\n" +

                                "Water Intake:\n" +
                                "• 3-4 Liters"
                );

            } else if (condition.contains("diabetes")) {

                resultText.setText(
                        "🥗 DIABETES DIET PLAN\n\n" +

                                "Breakfast:\n" +
                                "• Oats\n" +
                                "• Boiled Eggs\n\n" +

                                "Lunch:\n" +
                                "• Brown Rice\n" +
                                "• Vegetables\n\n" +

                                "Dinner:\n" +
                                "• Chapati\n" +
                                "• Dal\n\n" +

                                "Avoid:\n" +
                                "• Sugar\n" +
                                "• Soft Drinks\n" +
                                "• Sweets"
                );

            } else if (condition.contains("blood pressure")
                    || condition.contains("bp")) {

                resultText.setText(
                        "🥗 BLOOD PRESSURE DIET PLAN\n\n" +

                                "Eat:\n" +
                                "• Fruits\n" +
                                "• Vegetables\n" +
                                "• Low Salt Foods\n\n" +

                                "Avoid:\n" +
                                "• Pickles\n" +
                                "• Chips\n" +
                                "• Fast Food\n\n" +

                                "Water:\n" +
                                "• 2.5 Liters Daily"
                );


            }
            else if (condition.contains("cold")) {

                resultText.setText(
                        "🥗 COLD DIET PLAN\n\n" +

                                "Foods To Eat:\n" +
                                "• Warm Soup\n" +
                                "• Ginger Tea\n" +
                                "• Citrus Fruits\n\n" +

                                "Foods To Avoid:\n" +
                                "• Ice Cream\n" +
                                "• Cold Drinks\n\n" +

                                "Water Intake:\n" +
                                "• 3 Liters Daily\n\n" +

                                "Sleep:\n" +
                                "• 8 Hours\n\n" +

                                "Recovery Tips:\n" +
                                "• Keep Body Warm"
                );

            } else if (condition.contains("cough")) {

                resultText.setText(
                        "🥗 COUGH DIET PLAN\n\n" +

                                "Foods To Eat:\n" +
                                "• Honey\n" +
                                "• Warm Water\n" +
                                "• Soup\n\n" +

                                "Foods To Avoid:\n" +
                                "• Cold Foods\n" +
                                "• Fried Snacks\n\n" +

                                "Water Intake:\n" +
                                "• 3 Liters Daily\n\n" +

                                "Recovery Tips:\n" +
                                "• Avoid Dust"
                );

            } else if (condition.contains("stomach")) {

                resultText.setText(
                        "🥗 STOMACH PAIN DIET PLAN\n\n" +

                                "Foods To Eat:\n" +
                                "• Banana\n" +
                                "• Rice\n" +
                                "• Toast\n\n" +

                                "Foods To Avoid:\n" +
                                "• Spicy Foods\n" +
                                "• Oily Foods\n\n" +

                                "Water Intake:\n" +
                                "• 2.5 Liters\n\n" +

                                "Recovery Tips:\n" +
                                "• Eat Light Meals"
                );

            } else if (condition.contains("dengue")) {

                resultText.setText(
                        "🥗 DENGUE DIET PLAN\n\n" +

                                "Foods To Eat:\n" +
                                "• Papaya Leaf Juice\n" +
                                "• Coconut Water\n" +
                                "• Pomegranate\n\n" +

                                "Foods To Avoid:\n" +
                                "• Junk Food\n" +
                                "• Soft Drinks\n\n" +

                                "Water Intake:\n" +
                                "• 4 Liters Daily\n\n" +

                                "Recovery Tips:\n" +
                                "• Complete Rest"
                );

            } else if (condition.contains("typhoid")) {

                resultText.setText(
                        "🥗 TYPHOID DIET PLAN\n\n" +

                                "Foods To Eat:\n" +
                                "• Khichdi\n" +
                                "• Boiled Vegetables\n" +
                                "• Curd\n\n" +

                                "Foods To Avoid:\n" +
                                "• Spicy Foods\n" +
                                "• Fried Foods\n\n" +

                                "Water Intake:\n" +
                                "• 4 Liters Daily\n\n" +

                                "Recovery Tips:\n" +
                                "• Soft Foods Only"
                );

            } else if (condition.contains("migraine")) {

                resultText.setText(
                        "🥗 MIGRAINE DIET PLAN\n\n" +

                                "Foods To Eat:\n" +
                                "• Almonds\n" +
                                "• Leafy Vegetables\n\n" +

                                "Foods To Avoid:\n" +
                                "• Excess Coffee\n" +
                                "• Alcohol\n\n" +

                                "Sleep:\n" +
                                "• 8 Hours Daily\n\n" +

                                "Recovery Tips:\n" +
                                "• Reduce Screen Time"
                );

            }
            else {

                resultText.setText(
                        "No diet plan available for this condition.\n\n" +
                                "Try:\n" +
                                "• Fever\n" +
                                "• Diabetes\n" +
                                "• Blood Pressure"
                );
            }
        });
    }
}