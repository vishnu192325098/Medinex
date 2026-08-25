package com.example.medinex;

import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class ReminderActivity extends AppCompatActivity {

    EditText medicineName;
    Button saveReminderBtn;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_reminder);

        medicineName = findViewById(R.id.medicineName);
        saveReminderBtn = findViewById(R.id.saveReminderBtn);

        saveReminderBtn.setOnClickListener(v -> {

            String medicine = medicineName.getText().toString();

            if (medicine.isEmpty()) {
                Toast.makeText(
                        ReminderActivity.this,
                        "Enter Medicine Name",
                        Toast.LENGTH_SHORT
                ).show();
            } else {
                Toast.makeText(
                        ReminderActivity.this,
                        "Reminder Saved Successfully",
                        Toast.LENGTH_SHORT
                ).show();
            }
        });
    }
}