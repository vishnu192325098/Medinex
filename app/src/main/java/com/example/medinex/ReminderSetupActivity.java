package com.example.medinex;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TimePicker;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.util.Calendar;

public class ReminderSetupActivity extends AppCompatActivity {

    EditText medicineName;
    TimePicker timePicker;
    Button saveReminderBtn;
    Button historyBtn;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_reminder_setup);

        medicineName = findViewById(R.id.medicineName);
        timePicker = findViewById(R.id.timePicker);
        saveReminderBtn = findViewById(R.id.saveReminderBtn);
        historyBtn = findViewById(R.id.historyBtn);


        // --------------------------------
        // HISTORY BUTTON
        // --------------------------------

        historyBtn.setOnClickListener(v -> {

            Intent intent = new Intent(
                    ReminderSetupActivity.this,
                    ReminderHistoryActivity.class
            );

            startActivity(intent);
        });


        // --------------------------------
        // SAVE REMINDER
        // --------------------------------

        saveReminderBtn.setOnClickListener(v -> {

            String medicine =
                    medicineName.getText()
                            .toString()
                            .trim();

            if (medicine.isEmpty()) {

                Toast.makeText(
                        ReminderSetupActivity.this,
                        "Please enter medicine name",
                        Toast.LENGTH_LONG
                ).show();

                return;
            }


            // --------------------------------
            // GET TIME
            // --------------------------------

            int hour;
            int minute;

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {

                hour = timePicker.getHour();
                minute = timePicker.getMinute();

            } else {

                hour = timePicker.getCurrentHour();
                minute = timePicker.getCurrentMinute();
            }


            // --------------------------------
            // ALARM MANAGER
            // --------------------------------

            AlarmManager alarmManager =
                    (AlarmManager)
                            getSystemService(ALARM_SERVICE);


            // --------------------------------
            // ANDROID 12+ EXACT ALARM
            // --------------------------------

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {

                if (!alarmManager.canScheduleExactAlarms()) {

                    Toast.makeText(
                            ReminderSetupActivity.this,
                            "Please allow Alarms & reminders permission",
                            Toast.LENGTH_LONG
                    ).show();

                    Intent settingsIntent =
                            new Intent(
                                    Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM
                            );

                    startActivity(settingsIntent);

                    return;
                }
            }


            // --------------------------------
            // CREATE TIME
            // --------------------------------

            Calendar calendar =
                    Calendar.getInstance();

            calendar.set(
                    Calendar.HOUR_OF_DAY,
                    hour
            );

            calendar.set(
                    Calendar.MINUTE,
                    minute
            );

            calendar.set(
                    Calendar.SECOND,
                    0
            );

            calendar.set(
                    Calendar.MILLISECOND,
                    0
            );


            // If time already passed,
            // schedule for tomorrow.

            if (calendar.getTimeInMillis()
                    <= System.currentTimeMillis()) {

                calendar.add(
                        Calendar.DAY_OF_YEAR,
                        1
                );
            }


            // --------------------------------
            // SAVE TO DATABASE
            // --------------------------------

            String reminderTime =
                    String.format(
                            "%02d:%02d",
                            hour,
                            minute
                    );

            Reminder reminder =
                    new Reminder();

            reminder.medicineName = medicine;
            reminder.reminderTime = reminderTime;
            reminder.status = "Scheduled";
            reminder.triggeredAt = "";


            long reminderId =
                    AppDatabase
                            .getInstance(this)
                            .reminderDao()
                            .insert(reminder);


            // --------------------------------
            // NOTIFICATION INTENT
            // --------------------------------

            Intent notificationIntent =
                    new Intent(
                            ReminderSetupActivity.this,
                            NotificationReceiver.class
                    );

            notificationIntent.putExtra(
                    "medicine",
                    medicine
            );

            notificationIntent.putExtra(
                    "reminderId",
                    (int) reminderId
            );


            // --------------------------------
            // PENDING INTENT
            // --------------------------------

            PendingIntent pendingIntent =
                    PendingIntent.getBroadcast(
                            ReminderSetupActivity.this,
                            (int) reminderId,
                            notificationIntent,
                            PendingIntent.FLAG_UPDATE_CURRENT
                                    | PendingIntent.FLAG_IMMUTABLE
                    );


            // --------------------------------
            // SET ALARM
            // --------------------------------

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {

                alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        calendar.getTimeInMillis(),
                        pendingIntent
                );

            } else {

                alarmManager.setExact(
                        AlarmManager.RTC_WAKEUP,
                        calendar.getTimeInMillis(),
                        pendingIntent
                );
            }


            // --------------------------------
            // SUCCESS
            // --------------------------------

            Toast.makeText(
                    ReminderSetupActivity.this,
                    "Reminder saved for "
                            + reminderTime,
                    Toast.LENGTH_LONG
            ).show();

            medicineName.setText("");
        });
    }
}