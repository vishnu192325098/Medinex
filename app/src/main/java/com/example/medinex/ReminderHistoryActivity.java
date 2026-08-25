package com.example.medinex;

import android.os.Bundle;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import java.util.List;

public class ReminderHistoryActivity
        extends AppCompatActivity {

    TextView historyText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);

        setContentView(
                R.layout.activity_reminder_history
        );

        historyText =
                findViewById(
                        R.id.historyText
                );

        loadHistory();
    }


    private void loadHistory() {

        List<Reminder> reminders =
                AppDatabase
                        .getInstance(this)
                        .reminderDao()
                        .getAllReminders();


        StringBuilder builder =
                new StringBuilder();


        builder.append(
                "Total Reminders Found: "
        );

        builder.append(
                reminders.size()
        );

        builder.append("\n\n");


        if (reminders.isEmpty()) {

            builder.append(
                    "No reminders saved yet."
            );

        } else {

            for (Reminder reminder :
                    reminders) {

                builder.append(
                        "💊 Medicine: "
                );

                builder.append(
                        reminder.medicineName
                );

                builder.append("\n");


                builder.append(
                        "⏰ Time: "
                );

                builder.append(
                        reminder.reminderTime
                );

                builder.append("\n");


                builder.append(
                        "📌 Status: "
                );

                if (reminder.status != null &&
                        !reminder.status.isEmpty()) {

                    builder.append(
                            reminder.status
                    );

                } else {

                    builder.append(
                            "Scheduled"
                    );
                }

                builder.append("\n");


                if (reminder.triggeredAt != null &&
                        !reminder.triggeredAt.isEmpty()) {

                    builder.append(
                            "🔔 Triggered: "
                    );

                    builder.append(
                            reminder.triggeredAt
                    );

                    builder.append("\n");
                }


                builder.append(
                        "--------------------------"
                );

                builder.append("\n\n");
            }
        }


        historyText.setText(
                builder.toString()
        );
    }
}